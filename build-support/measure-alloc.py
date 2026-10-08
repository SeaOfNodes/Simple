"""Compile the shared measurement harness against one chapter's existing classes."""
from pathlib import Path
import json,os,re,subprocess,sys
root=Path(__file__).resolve().parents[1]; ch=int(sys.argv[1]); chapter=root/f'chapter{ch}'
if ch not in range(21,26): raise SystemExit("Dynamic measurement supports chapters 21-25")
out=root/'build/dynamic-stats'/str(ch);out.mkdir(parents=True,exist_ok=True)
src=(root/'build-support/AllocMetrics.java.in').read_text()
subs={'CHAPTER':str(ch),'OPSTART':'e.opStart(n)' if ch==25 else 'e._opStart[n._nid]',
      'OPLEN':'e.opLen(n)' if ch==25 else 'e._opLen[n._nid]',
      'CONSTRUCTOR':'new CodeGen(src,123L,true)' if ch==25 else 'new CodeGen(src,TypeInteger.BOT,123L,true)',
      'EXPORT':'c.exportELF(true,false)' if ch==25 else 'c.exportELF(null)',
      'EXTERNDATA':'c._externDataAddresses.put("counter",0x3800);c._externDataAddresses.put("errno",0x3808);' if ch==25 else '',
      'RESUME':'return c.loopTree().instSelect(cpu,"SystemV").GCM().localSched();' if ch==21 else 'return c.driver(CodeGen.Phase.LocalSched,cpu,"SystemV");',
      'COPYPOOLS':'''int cp=(e._bits.size()+15)&-16,sd=(cp+e._cpool.size()+15)&-16;
        System.arraycopy(e._cpool.buf(),0,image,cp,e._cpool.size());
        System.arraycopy(e._sdata.buf(),0,image,sd,e._sdata.size());''' if ch==25 else ''}
for a,b in subs.items():src=src.replace(a,b)
if ch<=22:
    src=src.replace('TypeInteger.BOT,123L,true','TypeInteger.BOT,123L').replace('s._fields[i]._t','s._fields[i]._type')
(out/'Measure.java').write_text(src,newline='\n')
cp=os.pathsep.join([str(chapter/'build/classes/main'),str(chapter/'build/classes/test'),str(chapter/'lib/*')])
subprocess.run(['javac','-cp',cp,'-d',str(out),str(out/'Measure.java')],check=True)
subprocess.run(['javac','-cp',cp,'-d',str(out),*[str(p) for p in (root/'isa/src/test-support/java/com/seaofnodes/isa/eval').glob('*.java')]],check=True)
if ch==25:
    p=root/'chapter25/src/test/java/com/seaofnodes/simple/spill'
    index=out/'index.tsv'
    rows=[]
    for line in (p/'cohorts.tsv').read_text().splitlines():
        if line.startswith('#') or not line:continue
        r=line.split('\t');rows.append('\t'.join(r[:4]+[str(p/r[5])]))
    test=(chapter/'src/test/java/com/seaofnodes/simple/Chapter25Test.java').read_text(encoding='utf-8')
    body=test.split('void testExternDataEncoding()',1)[1].split('@Test',1)[0]
    literal=re.search(r'new CheckedCodeGen\(("(?:\\.|[^"\\])*")\)',body)
    if literal is None: raise SystemExit('Cannot capture testExternDataEncoding source')
    extra=out/'extern-data.smp';extra.write_text(json.loads(literal[1])+'\n',encoding='utf-8',newline='\n')
    for cpu in ['arm','riscv']:rows.append('\t'.join(['Chapter25','Chapter25Test.testExternDataEncoding',cpu,'SystemV',str(extra)]))
    index.write_text('\n'.join(rows)+'\n')
else:index=out/'captured/index.tsv'
with (out/'dynamic.log').open('w') as log:
    subprocess.run(['java','-ea','-cp',str(out)+os.pathsep+cp,'Measure',str(index)],cwd=chapter,stdout=log,stderr=subprocess.STDOUT,check=True)
print(ch, 'done')
