package cl.fanlampmini;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;

public final class UiKit {
    public static final int BG=Color.rgb(15,21,32);
    public static final int CARD=Color.rgb(27,38,54);
    public static final int RAISED=Color.rgb(38,53,72);
    public static final int STROKE=Color.rgb(56,74,96);
    public static final int TEXT=Color.rgb(246,248,251);
    public static final int SUB=Color.rgb(157,173,190);
    public static final int MINT=Color.rgb(113,231,209);
    public static final int DARK_MINT=Color.rgb(16,47,48);
    private UiKit(){}

    public static int dp(Activity a,float v){return (int)(v*a.getResources().getDisplayMetrics().density+0.5f);}
    public static GradientDrawable background(int color,int radius,int borderColor){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);
        if(borderColor!=0)d.setStroke(1,borderColor);return d;
    }
    public static TextView label(Activity a,String value,int sp,int color,boolean bold){
        TextView t=new TextView(a);t.setText(value);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setIncludeFontPadding(true);
        return t;
    }
    public static TextView title(Activity a,String s,int sp){return label(a,s,sp,TEXT,true);}
    public static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public static LinearLayout row(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    public static LinearLayout card(Activity a){
        LinearLayout l=column(a);l.setPadding(dp(a,19),dp(a,19),dp(a,19),dp(a,19));
        l.setBackground(background(CARD,dp(a,23),STROKE));return l;
    }
    public static Button button(Activity a,String name,boolean primary,Runnable callback){
        Button b=new Button(a);b.setAllCaps(false);b.setText(name);b.setTextSize(14);
        b.setTextColor(primary?BG:TEXT);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setPadding(dp(a,12),0,dp(a,12),0);
        b.setMinHeight(0);b.setMinimumHeight(0);
        b.setBackground(background(primary?MINT:RAISED,dp(a,15),primary?0:STROKE));
        b.setOnClickListener(v->callback.run());return b;
    }
    public static SeekBar slider(Activity a,int progress){
        SeekBar s=new SeekBar(a);s.setMax(100);s.setProgress(progress);
        s.setProgressTintList(ColorStateList.valueOf(MINT));
        s.setThumbTintList(ColorStateList.valueOf(MINT));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(STROKE));
        return s;
    }
    public static LinearLayout.LayoutParams lp(Activity a,int width,int height){
        return new LinearLayout.LayoutParams(width<0?width:dp(a,width),height<0?height:dp(a,height));
    }
    public static void marginTop(Activity a,LinearLayout parent,View child,int top){
        LinearLayout.LayoutParams p=lp(a,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin=dp(a,top);parent.addView(child,p);
    }
    public static View line(Activity a){
        View v=new View(a);v.setBackgroundColor(STROKE);return v;
    }
    public static void darkBars(Activity a){
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);
        a.getWindow().getDecorView().setSystemUiVisibility(0);
    }
}
