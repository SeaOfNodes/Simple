"""Capture historical spill sources with a temporary instrumented test listener."""
from pathlib import Path
import subprocess,sys,os
root=Path(__file__).resolve().parents[1]
name=sys.argv[1].removeprefix('chapter')
if name not in ('21','22','23','24b'): raise SystemExit("Capture supports chapters 21, 22, 23, 24b")
chapter=root/f'chapter{name}'
out=root/'build/dynamic-stats'/name; out.mkdir(parents=True,exist_ok=True)
src=(chapter/'src/test/java/com/seaofnodes/simple/SpillStats.java').read_text()
needle='String test = ACTIVE._test.getTestClass().getSimpleName()+"."+ACTIVE._test.getMethodName();'
extra='''
        try {
            var dir=java.nio.file.Path.of("../build/dynamic-stats/CH/captured");
            java.nio.file.Files.createDirectories(dir);
            String name=test+"-"+cpu+"-"+abi+"-"+(_capture++)+".smp";
            java.nio.file.Files.writeString(dir.resolve(name),code._src);
            java.nio.file.Files.writeString(dir.resolve("index.tsv"),cohort+"\\t"+test+"\\t"+cpu+"\\t"+abi+"\\t"+name+"\\n",java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
        } catch(java.io.IOException e) { throw new RuntimeException(e); }
'''.replace('CH',name)
assert needle in src
src=src.replace(needle,needle+extra).replace('private static SpillStats ACTIVE;','private static SpillStats ACTIVE;\n    private static int _capture;')
(out/'SpillStats.java').write_text(src,newline='\n')
cp=os.pathsep.join([str(chapter/'build/classes/main'),str(chapter/'build/classes/test'),str(chapter/'lib/*')])
subprocess.run(['javac','-cp',cp,'-d',str(out),str(out/'SpillStats.java')],check=True)
idx=out/'captured/index.tsv'
if idx.exists(): idx.write_text('')
with (out/'capture.log').open('w') as log:
    subprocess.run(['java','-ea','-cp',str(out)+os.pathsep+cp,'com.seaofnodes.simple.SpillStats'],cwd=chapter,stdout=log,stderr=subprocess.STDOUT,check=True)
print(name,'captured',len(idx.read_text().splitlines()))
