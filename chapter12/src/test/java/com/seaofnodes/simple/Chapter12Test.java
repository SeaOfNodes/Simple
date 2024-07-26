package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter12Test {
    @Test
    public void testJig() {
        Parser parser = new Parser(
"""
arg=0;
struct fxaV1Ic3 {
}
fxaV1Ic3 tKRUMAeM=new fxaV1Ic3;
tKRUMAeM=tKRUMAeM;
while(2>0*-0<-arg<=false) if(-arg) if(-arg<=(-arg)) {
            arg=(-arg);
            while(false) {
            }
            {
                {}
                while(arg<arg>=(false/97)) break;
                break;
            }
        }
int eKG4lqt=-arg<--1<=----2;
eKG4lqt=-6;
int arg=--(--false<--0)<eKG4lqt>5;
eKG4lqt=-0;
{
    tKRUMAeM=new fxaV1Ic3;
    arg=-(-arg);
    flt erpUhf=0.9873326;
    {
        while(-false*arg-eKG4lqt!=58) if(----eKG4lqt) erpUhf=-0.84418744/erpUhf;
            else if(48>=eKG4lqt<=1/14----eKG4lqt) break;
                else break;
        erpUhf=(erpUhf)--0.35600418*--(erpUhf);
        if(-0/-eKG4lqt>=eKG4lqt---arg-arg) {
            if(--arg+7+--arg) {
            }
            else while(-1) erpUhf=erpUhf;
            tKRUMAeM=(tKRUMAeM);
            int rwPI=-eKG4lqt<=-(eKG4lqt+true);
        }
        if(false<=---8==--(false!=--arg)>=2) {
            return ---erpUhf==erpUhf;
        }
        erpUhf=-erpUhf;
    }
    {
        int U=-10+-----0!=eKG4lqt+25-13<-arg--eKG4lqt>=--arg;
        while(U<-0+(---true<=eKG4lqt<=-50>=false)) {
            U=U+-U!=-12<--(--43>=(false));
            if((eKG4lqt)>(---U*eKG4lqt<=0!=--arg)/-U) {
                erpUhf=-erpUhf+-erpUhf;
                if(-(--U)!=--arg+U>---arg>=true*-(--40!=-eKG4lqt<U+--6)) while(6/---U>=--1-62) erpUhf=-----(--0.87431<erpUhf>=-0.04622048<-0.7782522)-erpUhf*0.8976203+-0.26186156>erpUhf<0.23554772!=-0.37699592;
            }
            {
                int Su5Ap9KHJ=-U;
                while(Su5Ap9KHJ<---U) {
                    Su5Ap9KHJ=Su5Ap9KHJ+32+---eKG4lqt;
                }
            }
            if(-true--2) {
                tKRUMAeM=new fxaV1Ic3;
                while(true>U) continue;
            }
            else {
                fxaV1Ic3? DN8J9B=tKRUMAeM;
                flt eKG4lqt=0.17164475<erpUhf;
                {
                    if(!DN8J9B)DN8J9B=null;
                    else arg=U;
                }
                {
                    int CEdri=-U>12!=-arg;
                    while(CEdri<CEdri) {
                        CEdri=CEdri+--51;
                        if(!DN8J9B)erpUhf=--eKG4lqt;
                    }
                }
                U=-0;
            }
            fxaV1Ic3 RDAPoRG=tKRUMAeM;
            {
                int eKG4lqt=arg!=---U;
                while(eKG4lqt<arg*----(-eKG4lqt==--U*-U)>=false-5==-arg) {
                    eKG4lqt=eKG4lqt+-----arg;
                    while(U) {
                        while(15*-----4) U=--arg;
                        int gQqFyu1=--eKG4lqt;
                    }
                    {
                        int RDAPoRG=arg*0<--false;
                        while(RDAPoRG<--arg) {
                            RDAPoRG=RDAPoRG+--eKG4lqt;
                            eKG4lqt=--U;
                        }
                    }
                    if(14==arg<-3-true) while(2<=----false<=arg) return -erpUhf;
                    else arg=U;
                    if(-U) {
                        break;
                    }
                    {
                        if(2>eKG4lqt/arg!=--false<=--eKG4lqt) continue;
                        else erpUhf=-0.6544298;
                        eKG4lqt=--0==---U>=U*eKG4lqt==arg-arg;
                        if(-eKG4lqt) break;
                        else break;
                    }
                }
            }
            fxaV1Ic3? o0a0L;
            arg=--eKG4lqt;
            break;
        }
    }
    if(-((-arg)<=eKG4lqt)) while(--eKG4lqt) arg=---eKG4lqt!=5<-eKG4lqt;
    else return -arg;
    if(30) eKG4lqt=16>=-----arg!=-true<(eKG4lqt);
    flt l=0.17984325*-0.88223094;
    flt lg97e=l>-(--0.017727911==0.9812363)-(erpUhf!=-----0.19494736);
    erpUhf=--erpUhf!=---0.3488902;
    int DW=eKG4lqt==---eKG4lqt;
    if(DW*7<25*-22!=----96!=-2) return ---erpUhf;
    else return -DW/-arg;
}
//return 3.14;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testFloat() {
        Parser parser = new Parser(
"""
return 3.14;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testSquareRoot() {
        Parser parser = new Parser(
"""
flt guess = arg;
while( 1 ) {
    flt next = (arg/guess + guess)/2;
    if( next == guess ) break;
    guess = next;
}
return guess;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return Phi(Loop9,(flt)arg,(((ToFloat/Phi_guess)+Phi_guess)/2.0));", stop.toString());
        assertEquals(3.0, Evaluator.evaluate(stop,  9));
        assertEquals(1.414213562373095, Evaluator.evaluate(stop,  2));
    }

    @Test
    public void testFPOps() {
        Parser parser = new Parser(
"""
flt x = arg;
return x+1==x;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return ((flt)arg==(ToFloat+1.0));", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop, 1));
    }

}
