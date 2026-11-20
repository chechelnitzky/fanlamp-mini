package cl.fanlampmini;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

/** Accessible circular 0–6 fan speed dial inspired by, but not copying, the original control. */
public final class FanDialView extends View {
    public interface SpeedListener {void selected(int speed);}
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc=new RectF();
    private int speed;
    private SpeedListener listener;
    public FanDialView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    public void setSpeed(int value){speed=Math.max(0,Math.min(6,value));setContentDescription("Velocidad ventilador "+speed+" de 6");invalidate();}
    public void setListener(SpeedListener l){listener=l;}
    public int getSpeed(){return speed;}
    @Override protected void onMeasure(int w,int h){
        int size=resolveSize((int)(315*getResources().getDisplayMetrics().density),w);
        setMeasuredDimension(size,resolveSize(size,h));
    }
    private void paint(int color,float width,Paint.Style style){
        p.reset();p.setAntiAlias(true);p.setColor(color);p.setStyle(style);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),size=Math.min(w,h);
        float cx=w/2f,cy=h/2f+size*.015f,r=size*.355f,stroke=size*.058f;
        arc.set(cx-r,cy-r,cx+r,cy+r);

        paint(UiKit.STROKE,stroke,Paint.Style.STROKE);
        c.drawArc(arc,150,240,false,p);
        if(speed>0){
            paint(UiKit.MINT,stroke,Paint.Style.STROKE);
            c.drawArc(arc,150,240f*speed/6f,false,p);
        }
        for(int i=0;i<=30;i++){
            double angle=Math.toRadians(150+i*8);
            float x1=cx+(float)Math.cos(angle)*(r+stroke*.76f);
            float y1=cy+(float)Math.sin(angle)*(r+stroke*.76f);
            float x2=cx+(float)Math.cos(angle)*(r+stroke*(i%5==0?1.38f:1.20f));
            float y2=cy+(float)Math.sin(angle)*(r+stroke*(i%5==0?1.38f:1.20f));
            paint(i<=speed*5?UiKit.MINT:UiKit.SUB,size*(i%5==0?.010f:.007f),Paint.Style.STROKE);
            c.drawLine(x1,y1,x2,y2,p);
        }
        // Six elegant fan blades inside the dial.
        for(int i=0;i<6;i++){
            c.save();c.rotate(i*60,cx,cy);
            Path blade=new Path();
            blade.moveTo(cx+size*.022f,cy-size*.035f);
            blade.cubicTo(cx+size*.01f,cy-size*.13f,cx+size*.015f,cy-size*.24f,cx+size*.13f,cy-size*.20f);
            blade.cubicTo(cx+size*.22f,cy-size*.15f,cx+size*.16f,cy-size*.07f,cx+size*.06f,cy+size*.015f);
            blade.close();
            paint(i%2==0?Color.rgb(113,135,161):Color.rgb(87,111,143),0,Paint.Style.FILL);
            c.drawPath(blade,p);
            c.restore();
        }
        paint(UiKit.CARD,0,Paint.Style.FILL);c.drawCircle(cx,cy,size*.105f,p);
        paint(UiKit.STROKE,size*.016f,Paint.Style.STROKE);c.drawCircle(cx,cy,size*.085f,p);
        paint(UiKit.MINT,0,Paint.Style.FILL);c.drawCircle(cx,cy,size*.041f,p);
        // Highlight active arc endpoint.
        double a=Math.toRadians(150+speed*40);
        float kx=cx+(float)Math.cos(a)*r,ky=cy+(float)Math.sin(a)*r;
        paint(UiKit.TEXT,0,Paint.Style.FILL);c.drawCircle(kx,ky,size*.031f,p);
        // Discrete labels 0..6 outside the ticks.
        for(int i=0;i<=6;i++){
            a=Math.toRadians(150+i*40);
            float x=cx+(float)Math.cos(a)*(r+stroke*2.05f);
            float y=cy+(float)Math.sin(a)*(r+stroke*2.05f);
            paint(i==speed?UiKit.MINT:UiKit.SUB,0,Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
            p.setTextSize(size*.048f);
            c.drawText(String.valueOf(i),x,y-size*.048f/2-(p.ascent()+p.descent())/2,p);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN)return true;
        if(e.getActionMasked()!=MotionEvent.ACTION_UP)return true;
        float cx=getWidth()/2f,cy=getHeight()/2f+Math.min(getWidth(),getHeight())*.015f;
        float dx=e.getX()-cx,dy=e.getY()-cy;
        float radius=(float)Math.hypot(dx,dy);
        float size=Math.min(getWidth(),getHeight());
        if(radius<size*.20f || radius>size*.52f)return true; // Ignore accidental hub/outside taps.
        double degrees=(Math.toDegrees(Math.atan2(dy,dx))+360)%360;
        double position=(degrees-150+360)%360;
        if(position>240)position=position>300?0:240;
        setSpeed((int)Math.round(position/40));
        performClick();
        if(listener!=null)listener.selected(speed);
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
