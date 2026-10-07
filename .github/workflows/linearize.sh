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

# The donated Japanese index accompanies the English one when present.
readmes=(README.md)
if [[ -f "$repo/README.ja.md" ]]; then
  readmes+=(README.ja.md)
fi

last_committer_date='0 +0000'

git -C "$repo" ls-tree HEAD --name-only | grep '^chapter' |
while read -r chapter; do
  echo "Processing $chapter"

  chapter_number="$(sed 's/^chapter0*//' <<< "$chapter")"

  if [[ ! -f "$repo/$chapter/README.md" ]]; then
    echo "Missing $chapter/READMD.md" >&2
    echo "# Chapter $chapter_number" > "$repo/$chapter/README.md"
  fi
  chapter_title="$(grep '^#' "$repo/$chapter/README.md" | head -n1 | sed 's/^#* //')"

  # Extract the authors for this chapter from commit authors and the
  # Co-authored-by trailer.
  authors="$(git -C "$repo" log --format='%an <%ae>' --no-merges "$chapter")"
  co_authors="$(git -C "$repo" log --format=%B --no-merges "$chapter" |
    tr -d $'\r' | sed -n 's/^ *Co-authored-by: //p')"
  first_author="$(sort_authors <<< "$authors" | head -n1)"
  all_authors="$(sort_authors <<< "$authors"$'\n'"$co_authors")"

  message="$chapter_title"$'\n\n'
  while read -r author; do
    if [[ "$author" != "$first_author" ]]; then
      message+="Co-authored-by: $author"$'\n'
    fi
  done <<< "$all_authors"

  # Get the first and last dates this chapter was modified.
  root_files=("${readmes[@]}" LICENSE pom.xml .gitignore .dir-locals.el transpile-tests graph print isa build-support)
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
  mkdir -p docs
  # Grammar documents now sit beside their chapter README.
  for doc in docs/*.md; do
    [[ -f "$doc" ]] || continue
    sed -Ei 's,\(\.\./README(\.ja)?\.md([#)]),(README\1.md\2,g' "$doc"
    sed -Ei 's,\(\.\./\.\./(chapter[0-9]+[a-z]?)/docs/,(../\1/,g' "$doc"
  done
  # Keep the READMEs and supporting notes together, including Japanese copies.
  mv *.md docs/
  mv docs chapter_docs
  if [[ -d appendix ]]; then
    mv appendix chapter_docs/
    sed -Ei 's,\(\.\./docs/,(../,g' chapter_docs/appendix/*.md
  fi
  rm pom.xml || :
  git add .

  # Add the shared files in the root, except for the indexes and pom.xml.
  root_files=(LICENSE .gitignore .dir-locals.el transpile-tests graph print isa build-support)
  cp -R "${root_files[@]/#/"$repo/"}" .
  git add "${root_files[@]}"

  # Fix the English/Japanese indexes, chapter READMEs, and pom.xml.
  if git rev-parse HEAD >/dev/null 2>/dev/null; then
    # Restore the documentation and pom.xml from the previous chapter.
    git diff --staged --diff-filter=D --name-only -- docs "${readmes[@]}" pom.xml | xargs git checkout -q HEAD
  else
    # This is the first commit; setup shared files.
    cp "${readmes[@]/#/"$repo/"}" "$repo/pom.xml" .
    mkdir docs
    # Shared type-domain illustrations are referenced from chapter READMEs.
    cp "$repo"/docs/type-*.svg docs/
    git add docs/type-*.svg
    if [[ -f "$repo/docs/japanese-translation.md" ]]; then
      cp "$repo/docs/japanese-translation.md" docs/
      git add docs/japanese-translation.md
    fi
    # Remove links to chapters.
    sed -Ei 's,\[Chapter ([0-9]+[a-z]?)\]\(chapter0?\1/README\.md\),Chapter \1,' README.md
    if [[ -f README.ja.md ]]; then
      sed -Ei 's,\[([^]]+)\]\(chapter[0-9]+[a-z]?/README(\.ja)?\.md\),\1,g' README.ja.md
    fi
    # Change to a JAR and delete the modules, for a single-project structure.
    sed -i -e 's,<packaging>pom</packaging>,<packaging>jar</packaging>,' \
           -e '/<modules>/,/^$/d' pom.xml
  fi
  # ISA sources enter the standalone Maven build with instruction encoding.
  if [[ "$chapter_number" == 21 ]]; then
    sed -i '/<source>${simple.print.directory}\/src\/main\/java<\/source>/a\                                <source>${project.basedir}/isa/src/main/java</source>' pom.xml
    sed -i '/<\/executions>/i\                    <execution>\
                        <id>shared-isa-test-sources</id>\
                        <phase>generate-test-sources</phase>\
                        <goals><goal>add-test-source</goal></goals>\
                        <configuration><sources>\
                            <source>${project.basedir}/isa/src/test-support/java</source>\
                        </sources></configuration>\
                    </execution>' pom.xml
  fi
  git mv chapter_docs "docs/$chapter"
  # Restore the link to this chapter.
  sed -Ei "s,^\* Chapter $chapter_number: ,* [Chapter $chapter_number](docs/$chapter/README.md): ," README.md
  if [[ -f README.ja.md ]]; then
    translated=README.md
    if [[ -f "docs/$chapter/README.ja.md" ]]; then
      translated=README.ja.md
    fi
    sed -Ei "s,^\* 第${chapter_number}章：,* [第${chapter_number}章](docs/$chapter/$translated)：," README.ja.md
  fi
  # Repair links for this chapter.
  for doc in "docs/$chapter"/README*.md; do
    sed -Ei 's,\bdocs/,,g' "$doc"
    sed -Ei 's,\(\.\./(isa|graph|print)/,(../../\1/,g' "$doc"
    sed -Ei 's,\(\.\./README(\.ja)?\.md([#)]),(../../README\1.md\2,g' "$doc"
    # Sources live at the root in a standalone chapter checkout.
    sed -Ei 's,\((\./)?src/,(../../src/,g' "$doc"
  done
  git add "${readmes[@]}" "docs/$chapter" pom.xml

  # Create a commit for the chapter, using the metadata.
  if [[ ! "$first_author" =~ ^(.*)\ \<(.*)\>$ ]]; then
    echo "First author does not match pattern" >&2
    exit 1
  fi
  name="${BASH_REMATCH[1]}"
  email="${BASH_REMATCH[2]}"
  GIT_AUTHOR_NAME="$name" GIT_AUTHOR_EMAIL="$email" GIT_AUTHOR_DATE="$author_date" \
  GIT_COMMITTER_NAME="$name" GIT_COMMITTER_EMAIL="$email" GIT_COMMITTER_DATE="$committer_date" \
  git commit -q -m "$message"

  # Tag the chapter, so it can be stably linked.
  git tag "linear-$chapter"
done

echo 'Done!'
