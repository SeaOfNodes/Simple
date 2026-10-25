# IntelliJ

Open the repository folder as an existing IntelliJ project. Select JDK 21 in
**File > Project Structure > Project SDK** if needed. The native `.iml` setup
includes every numbered chapter and the shared Java code; no Maven import is
needed. Each chapter has separate main and test output under `build/idea` and
never depends on another chapter's compiler. Make's output stays separate.

On a fresh checkout, run `make lib` to fetch JUnit, then close the project and run:

```sh
python build-support/idea.py
```

Reopen the repository folder. The script refreshes the module list and test
configurations, and sets the JUnit template's working directory to
`$MODULE_WORKING_DIR$`. It preserves the selected SDK and existing personal run
configurations and breakpoints. Original settings are backed up in
`build/idea-backup`. Run it again after adding or renaming chapters, with the
project closed so IntelliJ cannot overwrite the new list with its old one.
After a chapter move, use **Build > Rebuild Project** once to remove old compiled
classes from reused chapter directories.

Open a chapter's `src/test/java` file and use the gutter's **Debug** action on a
test method. Its configuration should use that chapter's module. You can also
select **chapter23 field write** to debug
`PrefixUpdateTest.testMethodFieldWrites`, or **chapterXX tests** to run that
chapter's suite. These shared configurations set the module, working directory,
assertions and build step explicitly. Suite discovery is limited to that module.

Old personal run configurations may still name the former combined chapter25.
Recreate those from the intended chapter's test gutter, or change both their
module and working directory in **Run > Edit Configurations**. The same Java
class names occur in many chapters, so a class name alone cannot identify the
intended snapshot.

Tests that compile native code still need the C toolchain on IntelliJ's PATH.
Tests using Chapter 28b's native runtime or saved `sys` library also need the
Make prerequisites: run `make -C chapter28b release CTAGS=` before debugging
those tests. IntelliJ's Java build does not build the native runtime or library.

For the JUnit working directory and module classpath settings, see
[JetBrains' JUnit configuration reference](https://www.jetbrains.com/help/idea/run-debug-configuration-junit.html).
