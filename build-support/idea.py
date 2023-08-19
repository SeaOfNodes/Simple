#!/usr/bin/env python3
"""Refresh the native IntelliJ project after adding or moving chapters.

Close the project first: IDEA can otherwise save its old module list over ours.
No Maven import is needed. Personal settings stay in the ignored workspace.xml.
"""

from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent


def save(path, text):
    path = ROOT / path
    data = text.encode("utf-8")
    if path.exists() and path.read_bytes() == data:
        return
    if path.exists():
        backup = ROOT / "build/idea-backup" / path.relative_to(ROOT)
        backup.parent.mkdir(parents=True, exist_ok=True)
        # Keep the original settings across repeated refreshes.
        if not backup.exists():
            shutil.copyfile(path, backup)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def xml(path, root):
    ET.indent(root, space="  ")
    save(path, '<?xml version="1.0" encoding="UTF-8"?>\n'
         + ET.tostring(root, encoding="unicode") + "\n")


def module(name, directory, dependencies=(), junit=None, main="src/main/java"):
    root = ET.Element("module", type="JAVA_MODULE", version="4")
    manager = ET.SubElement(root, "component", name="NewModuleRootManager",
                            **{"inherit-compiler-output": "false"})
    relative = "../" * len(Path(directory).parts)
    output = "file://$MODULE_DIR$/" + relative + "build/idea/"
    ET.SubElement(manager, "output", url=output + "production/" + name)
    ET.SubElement(manager, "output-test", url=output + "test/" + name)
    ET.SubElement(manager, "exclude-output")
    content = ET.SubElement(manager, "content", url="file://$MODULE_DIR$")
    for source, test in [(main, "false"), ("src/test/java", "true")]:
        if (ROOT / directory / source).is_dir():
            ET.SubElement(content, "sourceFolder", url="file://$MODULE_DIR$/" + source,
                          isTestSource=test)
    for excluded in ["build", "target", "lib"] + (["src/test-support"] if name == "isa" else []):
        ET.SubElement(content, "excludeFolder", url="file://$MODULE_DIR$/" + excluded)
    ET.SubElement(manager, "orderEntry", type="inheritedJdk")
    ET.SubElement(manager, "orderEntry", type="sourceFolder", forTests="false")
    for dependency, scope in dependencies:
        attributes = {"type": "module", "module-name": dependency}
        if scope:
            attributes["scope"] = scope
        ET.SubElement(manager, "orderEntry", **attributes)
    if junit:
        entry = ET.SubElement(manager, "orderEntry", type="module-library", scope="TEST")
        library = ET.SubElement(entry, "library", name="JUnit4")
        classes = ET.SubElement(library, "CLASSES")
        for jar in ["junit-4.12.jar", "hamcrest-core-1.3.jar"]:
            ET.SubElement(classes, "root", url="jar://$MODULE_DIR$/" + junit + "/" + jar + "!/")
        ET.SubElement(library, "JAVADOC")
        ET.SubElement(library, "SOURCES")
    path = directory + "/" + name + ".iml"
    xml(path, root)
    return path


def run(name, chapter, test_class=None, method=None):
    root = ET.Element("component", name="ProjectRunConfigurationManager")
    config = ET.SubElement(root, "configuration", default="false", name=name,
                           type="JUnit", factoryName="JUnit")
    ET.SubElement(config, "module", name=chapter)
    options = {"PACKAGE_NAME": "com.seaofnodes.simple",
               "TEST_OBJECT": "method" if method else "package",
               "VM_PARAMETERS": "-ea", "WORKING_DIRECTORY": "$PROJECT_DIR$/" + chapter}
    if test_class:
        options["MAIN_CLASS_NAME"] = "com.seaofnodes.simple." + test_class
    if method:
        options["METHOD_NAME"] = method
    for key, value in options.items():
        ET.SubElement(config, "option", name=key, value=value)
    scope = ET.SubElement(config, "TEST_SEARCH_SCOPE")
    ET.SubElement(scope, "value", defaultName="singleModule")
    before = ET.SubElement(config, "method", v="2")
    ET.SubElement(before, "option", name="Make", enabled="true")
    xml(".run/" + name + ".run.xml", root)


def main():
    chapters = sorted(path.name for path in ROOT.iterdir()
                      if re.fullmatch(r"chapter\d\d[a-z]?", path.name)
                      and (path / "pom.xml").is_file())
    paths = []
    for chapter in chapters:
        dependencies = [("graph", None), ("print", None)]
        if "isa/isa.mk" in (ROOT / chapter / "Makefile").read_text(encoding="utf-8"):
            dependencies += [("isa", None), ("isa-eval", "TEST")]
        paths.append(module(chapter, chapter, dependencies, "lib"))
        run(chapter + " tests", chapter)
    paths.append(module("print", "print"))
    paths.append(module("graph", "graph", [("print", None)]))
    paths.append(module("isa", "isa", [("isa-eval", "TEST")]))
    paths.append(module("isa-eval", "isa/src/test-support", [("isa", None)], main="java"))
    paths.append(module("transpile-tests", "transpile-tests", junit="../chapter01/lib"))
    project = ET.Element("project", version="4")
    manager = ET.SubElement(project, "component", name="ProjectModuleManager")
    modules = ET.SubElement(manager, "modules")
    for path in paths:
        ET.SubElement(modules, "module", fileurl="file://$PROJECT_DIR$/" + path,
                      filepath="$PROJECT_DIR$/" + path)
    xml(".idea/modules.xml", project)
    run("chapter23 field write", "chapter23", "PrefixUpdateTest", "testMethodFieldWrites")

    # SDK names are local to the IDE installation. Preserve an existing choice;
    # a new checkout asks the user to select an installed JDK 21 in Project Structure.
    if not (ROOT / ".idea/misc.xml").exists():
        save(".idea/misc.xml", '<project version="4">\n'
             '  <component name="ProjectRootManager" version="2" languageLevel="JDK_21">\n'
             '    <output url="file://$PROJECT_DIR$/build/idea" />\n'
             '  </component>\n</project>\n')

    # New gutter runs must use the selected module's directory, not the repo root.
    # Do not rewrite existing personal configurations, breakpoints or window state.
    workspace = ROOT / ".idea/workspace.xml"
    project = ET.parse(workspace).getroot() if workspace.exists() else ET.Element("project", version="4")
    manager = project.find("component[@name='RunManager']")
    if manager is None:
        manager = ET.SubElement(project, "component", name="RunManager")
    template = manager.find("configuration[@default='true'][@type='JUnit']")
    if template is None:
        template = ET.SubElement(manager, "configuration", default="true", type="JUnit", factoryName="JUnit")
    option = template.find("option[@name='WORKING_DIRECTORY']")
    if option is None:
        option = ET.SubElement(template, "option", name="WORKING_DIRECTORY")
    option.set("value", "$MODULE_WORKING_DIR$")
    if template.find("option[@name='VM_PARAMETERS']") is None:
        ET.SubElement(template, "option", name="VM_PARAMETERS", value="-ea")
    if template.find("TEST_SEARCH_SCOPE") is None:
        scope = ET.SubElement(template, "TEST_SEARCH_SCOPE")
        ET.SubElement(scope, "value", defaultName="singleModule")
    if template.find("method") is None:
        before = ET.SubElement(template, "method", v="2")
        ET.SubElement(before, "option", name="Make", enabled="true")
    xml(".idea/workspace.xml", project)
    print(f"Configured {len(chapters)} chapters and 5 shared/tool modules. Select JDK 21 and reopen the project.")


if __name__ == "__main__":
    main()
