#!/usr/bin/env bash
set -eEuo pipefail

sort_authors() {
  # Unify author aliases.
  sed -e 's/^dibyendumajumdar <mobile@majumdar\.org\.uk>$/Dibyendu Majumdar <mobile@majumdar.org.uk>/g' \
       -e 's/^Dibyendu Majumdar <dibyendumajumdar@users\.noreply\.github\.com>$/Dibyendu Majumdar <mobile@majumdar.org.uk>/g' |
  # Rank by number of commits.
  sort | uniq -c | sort -nr | sed -E 's/^ *[0-9]+ //'
}

IFS=$'\n'

mkdir linear
cd linear
git init -q -b main

repo=..

last_committer_date='0 +0000'

git -C "$repo" ls-tree HEAD --name-only | grep '^chapter' |
while read -r chapter; do
  echo "Processing $chapter"

  chapter_number="$(sed 's/^chapter0*//' <<< "$chapter")"
  chapter_title="$(head -n1 "$repo/$chapter/README.md" | sed 's/^# //')"

  # Extract the authors for this chapter from commit authors and the
  # Co-authored-by trailer.
  authors="$(git -C "$repo" log --format='%an <%ae>' --no-merges "$chapter")"
  co_authors="$(git -C "$repo" log --format=%B --no-merges "$chapter" |
    tr -d $'\r' | ( grep '^ *Co-authored-by: ' || : ) | cut -d' ' -f2-)"
  first_author="$(sort_authors <<< "$authors" | head -n1)"
  all_authors="$(sort_authors <<< "$authors"$'\n'"$co_authors")"

  message="$chapter_title"$'\n\n'
  while read -r author; do
    if [[ "$author" != "$first_author" ]]; then
      message+="Co-authored-by: $author"$'\n'
    fi
  done <<< "$all_authors"

  # Get the first and last dates this chapter was modified.
  root_files=(README.md LICENSE pom.xml .gitignore .dir-locals.el transpile-tests)
  author_root_files=()
  if [[ $chapter = chapter01 ]]; then
    # Only include the commit date of the root files for chapter01.
    author_root_files+=("${root_files[@]}")
  fi
  author_date="$(git -C "$repo" log --format=%ad --date=raw "$chapter" "${author_root_files[@]}" | tail -n1)"
  committer_date="$(git -C "$repo" log --format=%ad --date=raw -1 "$chapter" "${root_files[@]}")"
  if [[ $committer_date < $last_committer_date ]]; then
    committer_date="$last_committer_date"
  else
    last_committer_date="$committer_date"
  fi

  # Add the files for the chapter.
  git rm -qr --ignore-unmatch .
  cp -R "$repo/$chapter/." .
  mv README.md docs/
  mv docs chapter_docs
  rm pom.xml
  git add .

  # Add the shared files in the root, except for README.md and pom.xml.
  root_files=(LICENSE .gitignore .dir-locals.el transpile-tests)
  cp -R "${root_files[@]/#/"$repo/"}" .
  git add "${root_files[@]}"

  # Fix README.md, docs/chapter??/README.md, and pom.xml.
  if git rev-parse HEAD >/dev/null 2>/dev/null; then
    # Restore the documentation and pom.xml from the previous chapter.
    git diff --staged --diff-filter=D --name-only -- docs README.md pom.xml | xargs git checkout -q HEAD
  else
    # This is the first commit; setup shared files.
    cp "$repo"/{README.md,pom.xml} .
    mkdir docs
    # Remove links to chapters.
    sed -Ei 's,\[Chapter ([0-9]+)\]\(chapter0?\1/README\.md\),Chapter \1,' README.md
    # Change to a JAR and delete the modules, for a single-project structure.
    sed -i -e 's,<packaging>pom</packaging>,<packaging>jar</packaging>,' \
            -e '/<modules>/,/^$/d' pom.xml
  fi
  git mv chapter_docs "docs/$chapter"
  # Restore the link to this chapter.
  sed -Ei "s,^\* Chapter $chapter_number: ,* [Chapter $chapter_number](docs/$chapter/README.md): ," README.md
  # Repair links for this chapter.
  sed -Ei 's,\bdocs/,,' "docs/$chapter/README.md"
  git add README.md "docs/$chapter/README.md" pom.xml

  if [[ ! "$first_author" =~ ^(.*)\ \<(.*)\>$ ]]; then
    echo "First author does not match pattern" >&2
    exit 1
  fi
  name="${BASH_REMATCH[1]}"
  email="${BASH_REMATCH[2]}"
  GIT_AUTHOR_NAME="$name" GIT_AUTHOR_EMAIL="$email" GIT_AUTHOR_DATE="$author_date" \
  GIT_COMMITTER_NAME="$name" GIT_COMMITTER_EMAIL="$email" GIT_COMMITTER_DATE="$committer_date" \
  git commit -q -m "$message"
done
