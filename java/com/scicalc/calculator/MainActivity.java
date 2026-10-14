package com.scicalc.calculator;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 科学计算器主界面。
 * 布局全部用代码构建，不依赖 layout XML，方便手动 aapt2 构建链。
 */
public class MainActivity extends Activity {

    private static final int COL_BG      = 0xFF0E1116;
    private static final int COL_DISPLAY = 0xFF161D25;
    private static final int COL_NUM     = 0xFF2A323C;
    private static final int COL_FUNC    = 0xFF394A55;
    private static final int COL_OP      = 0xFF1565C0;
    private static final int COL_EQ      = 0xFFF57C00;
    private static final int COL_CLEAR   = 0xFFB3261E;
    private static final int COL_TEXT    = 0xFFFFFFFF;
    private static final int COL_SUB     = 0xFF8FA3B0;
    private static final int COL_OK      = 0xFF26A69A;
    private static final int COL_ERR     = 0xFFFF7043;
    private static final int COL_ROW_ON  = 0xFF20303F;   // 进制模式当前编辑行的底色

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT;

    /** 退格时要整体删除的词元（长的放前面） */
    private static final String[] MULTI_TOKENS = {
            "asin(", "acos(", "atan(", "sin(", "cos(", "tan(",
            "log(", "ln(", "sqrt(", "√(", "mod", "π", "e", "1/("
    };

    private TextView exprView;
    private TextView resultView;
    private Button degBtn;

    private final StringBuilder input = new StringBuilder();
    private boolean degree = true;
    private boolean justEvaluated = false;
    private String lastText = "";

    // ---- 模式切换 ----
    private LinearLayout calcPanel;
    private LinearLayout convPanel;
    private Button modeCalcBtn;
    private Button modeConvBtn;

    // ---- 进制转换面板 ----
    private LinearLayout[] convRows;
    private TextView[] convValues;
    private TextView convHintView;
    private final List<Button> convKeys = new ArrayList<Button>();

    private final StringBuilder convInput = new StringBuilder();
    private int convBase = BaseConv.DEC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refresh();
        refreshConv();
    }

    // ------------------------------------------------------------------ 界面

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COL_BG);
        root.setPadding(dp(6), dp(6), dp(6), dp(6));

        root.addView(buildModeBar(), new LinearLayout.LayoutParams(MATCH, WRAP));

        calcPanel = new LinearLayout(this);
        calcPanel.setOrientation(LinearLayout.VERTICAL);
        buildCalcPanel(calcPanel);
        root.addView(calcPanel, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        convPanel = new LinearLayout(this);
        convPanel.setOrientation(LinearLayout.VERTICAL);
        buildConvPanel(convPanel);
        root.addView(convPanel, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        setMode(true);
        return root;
    }

    /** 计算模式面板：显示屏 + 8 行键盘（原 buildUi 的主体）。 */
    private void buildCalcPanel(LinearLayout root) {
        // ---- 显示屏 ----
        LinearLayout disp = new LinearLayout(this);
        disp.setOrientation(LinearLayout.VERTICAL);
        disp.setGravity(Gravity.BOTTOM | Gravity.END);
        disp.setBackground(rounded(COL_DISPLAY, 16));
        disp.setPadding(dp(14), dp(10), dp(14), dp(10));

        exprView = new TextView(this);
        exprView.setTextColor(COL_SUB);
        exprView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        exprView.setGravity(Gravity.END);
        exprView.setMaxLines(3);

        resultView = new TextView(this);
        resultView.setTextColor(COL_TEXT);
        resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        resultView.setGravity(Gravity.END);
        resultView.setSingleLine(true);

        disp.addView(exprView, new LinearLayout.LayoutParams(MATCH, WRAP));
        disp.addView(resultView, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(disp, new LinearLayout.LayoutParams(MATCH, 0, 1.1f));

        // ---- 键盘 ----
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(0, dp(6), 0, 0);

        pad.addView(makeRow(
                new String[]{"DEG", "(", ")", "DEL", "C"},
                new String[]{"deg", "ins:(", "ins:)", "back", "clear"},
                new int[]{COL_FUNC, COL_FUNC, COL_FUNC, COL_CLEAR, COL_CLEAR}));

        pad.addView(makeRow(
                new String[]{"sin", "cos", "tan", "π", "÷"},
                new String[]{"ins:sin(", "ins:cos(", "ins:tan(", "ins:π", "ins:/"},
                new int[]{COL_FUNC, COL_FUNC, COL_FUNC, COL_FUNC, COL_OP}));

        pad.addView(makeRow(
                new String[]{"√", "x²", "x^y", "e", "×"},
                new String[]{"ins:√(", "ins:^2", "ins:^", "ins:e", "ins:*"},
                new int[]{COL_FUNC, COL_FUNC, COL_FUNC, COL_FUNC, COL_OP}));

        pad.addView(makeRow(
                new String[]{"ln", "log", "7", "8", "9"},
                new String[]{"ins:ln(", "ins:log(", "ins:7", "ins:8", "ins:9"},
                new int[]{COL_FUNC, COL_FUNC, COL_NUM, COL_NUM, COL_NUM}));

        pad.addView(makeRow(
                new String[]{"1/x", "n!", "4", "5", "6"},
                new String[]{"ins:1/(", "ins:!", "ins:4", "ins:5", "ins:6"},
                new int[]{COL_FUNC, COL_FUNC, COL_NUM, COL_NUM, COL_NUM}));

        pad.addView(makeRow(
                new String[]{"mod", "%", "1", "2", "3"},
                new String[]{"ins:mod", "ins:%", "ins:1", "ins:2", "ins:3"},
                new int[]{COL_FUNC, COL_FUNC, COL_NUM, COL_NUM, COL_NUM}));

        pad.addView(makeRow(
                new String[]{"±", ".", "0", "−", "+"},
                new String[]{"sign", "ins:.", "ins:0", "ins:-", "ins:+"},
                new int[]{COL_FUNC, COL_NUM, COL_NUM, COL_OP, COL_OP}));

        pad.addView(makeRow(
                new String[]{"="},
                new String[]{"equals"},
                new int[]{COL_EQ}));

        root.addView(pad, new LinearLayout.LayoutParams(MATCH, 0, 3.0f));
    }
    // ------------------------------------------------------------------ 模式切换

    /** 顶部「计算 / 进制」切换条。 */
    private View buildModeBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        modeCalcBtn = makeModeButton("计算");
        modeConvBtn = makeModeButton("进制");

        modeCalcBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(true);
            }
        });
        modeConvBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(false);
            }
        });

        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        l1.setMargins(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        l2.setMargins(dp(3), dp(3), dp(3), dp(3));
        bar.addView(modeCalcBtn, l1);
        bar.addView(modeConvBtn, l2);
        return bar;
    }

    private Button makeModeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(COL_TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(selectedBg(COL_OP));
        return b;
    }

    /** 三态背景：按下 > 选中 > 普通。选中态用来高亮当前模式。 */
    private Drawable selectedBg(int color) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(lighten(color, 0.24f), 12));
        s.addState(new int[]{android.R.attr.state_selected}, rounded(color, 12));
        s.addState(new int[]{}, rounded(COL_NUM, 12));
        return s;
    }

    private void setMode(boolean calc) {
        if (calcPanel == null || convPanel == null) return;
        calcPanel.setVisibility(calc ? View.VISIBLE : View.GONE);
        convPanel.setVisibility(calc ? View.GONE : View.VISIBLE);
        if (modeCalcBtn != null) modeCalcBtn.setSelected(calc);
        if (modeConvBtn != null) modeConvBtn.setSelected(!calc);
    }

    /** 进制模式下按返回键 -> 先回到计算模式，而不是直接退出应用。 */
    @Override
    public void onBackPressed() {
        if (convPanel != null && convPanel.getVisibility() == View.VISIBLE) {
            setMode(true);
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ 进制转换面板

    /** 进制模式：4 行进制度量 + 提示行 + 16 进制键盘。 */
    private void buildConvPanel(LinearLayout root) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setGravity(Gravity.CENTER_VERTICAL);
        list.setBackground(rounded(COL_DISPLAY, 16));
        list.setPadding(dp(8), dp(8), dp(8), dp(8));

        convRows = new LinearLayout[BaseConv.BASES.length];
        convValues = new TextView[BaseConv.BASES.length];

        for (int i = 0; i < BaseConv.BASES.length; i++) {
            final int base = BaseConv.BASES[i];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));

            TextView tag = new TextView(this);
            tag.setText(BaseConv.NAMES[i]);
            tag.setTextColor(COL_SUB);
            tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

            TextView val = new TextView(this);
            val.setTextColor(COL_TEXT);
            val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            val.setGravity(Gravity.END);
            val.setMaxLines(3);
            val.setEllipsize(TextUtils.TruncateAt.END);

            row.addView(tag, new LinearLayout.LayoutParams(dp(46), WRAP));
            row.addView(val, new LinearLayout.LayoutParams(0, WRAP, 1f));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectConvBase(base);
                }
            });

            list.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
            convRows[i] = row;
            convValues[i] = val;
        }
        root.addView(list, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        convHintView = new TextView(this);
        convHintView.setTextColor(COL_SUB);
        convHintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        convHintView.setPadding(dp(10), dp(6), dp(10), dp(6));
        root.addView(convHintView, new LinearLayout.LayoutParams(MATCH, WRAP));

        root.addView(buildConvPad(), new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    /** 16 进制键盘；当前进制用不到的数字键会在 refreshConv() 里被灰掉。 */
    private View buildConvPad() {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(0, dp(4), 0, 0);

        pad.addView(convRow(new String[]{"7", "8", "9", "A", "B"},
                new int[]{COL_NUM, COL_NUM, COL_NUM, COL_FUNC, COL_FUNC}));
        pad.addView(convRow(new String[]{"4", "5", "6", "C", "D"},
                new int[]{COL_NUM, COL_NUM, COL_NUM, COL_FUNC, COL_FUNC}));
        pad.addView(convRow(new String[]{"1", "2", "3", "E", "F"},
                new int[]{COL_NUM, COL_NUM, COL_NUM, COL_FUNC, COL_FUNC}));
        pad.addView(convRow(new String[]{"±", "0", "DEL", "清空"},
                new int[]{COL_FUNC, COL_NUM, COL_CLEAR, COL_CLEAR}));

        // 键盘整体固定高度，把富余空间留给上面的 4 行进制度量
        for (int i = 0; i < pad.getChildCount(); i++) {
            pad.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(52)));
        }
        return pad;
    }

    /** 一行进制键盘；数字键 action = "cv:d:<字符>"，用于回填与灰化判定。 */
    private View convRow(String[] labels, int[] colors) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            String lab = labels[i];
            String action;
            if ("±".equals(lab))            action = "cv:sign";
            else if ("DEL".equals(lab))     action = "cv:back";
            else if ("清空".equals(lab))    action = "cv:clear";
            else                            action = "cv:d:" + lab;

            Button b = makeButton(lab, action, colors[i]);
            if (action.startsWith("cv:d:")) {
                b.setTag(Character.valueOf(lab.charAt(0)));
                convKeys.add(b);
            }
            row.addView(b);
        }
        return row;
    }

    private void onConvAction(String a) {
        if (a.startsWith("cv:d:")) {
            char c = a.charAt(5);
            if (!BaseConv.isValidDigit(c, convBase)) return;
            if (convInput.length() >= BaseConv.MAX_LEN) return;
            convInput.append(c);
        } else if (a.equals("cv:sign")) {
            if (convInput.length() > 0 && convInput.charAt(0) == '-') {
                convInput.deleteCharAt(0);
            } else {
                convInput.insert(0, '-');
            }
        } else if (a.equals("cv:back")) {
            if (convInput.length() > 0) {
                convInput.deleteCharAt(convInput.length() - 1);
            }
        } else if (a.equals("cv:clear")) {
            convInput.setLength(0);
        }
        refreshConv();
    }

    /** 切换输入进制：把当前输入换算成新进制的写法，数值本身不变。 */
    private void selectConvBase(int base) {
        if (base == convBase) return;
        String cur = convInput.toString();
        if (cur.length() > 0) {
            try {
                BigInteger v = BaseConv.parse(cur, convBase);
                convInput.setLength(0);
                convInput.append(BaseConv.format(v, base));
            } catch (IllegalArgumentException e) {
                convInput.setLength(0);          // 例如只按了 ± 还没输入数字
            }
        }
        convBase = base;
        refreshConv();
    }

    private void refreshConv() {
        if (convValues == null) return;

        String raw = convInput.toString();
        BigInteger value = null;
        String err = "";
        if (raw.length() > 0 && !"-".equals(raw)) {
            try {
                value = BaseConv.parse(raw, convBase);
            } catch (IllegalArgumentException e) {
                err = e.getMessage();
            }
        }

        for (int i = 0; i < BaseConv.BASES.length; i++) {
            int base = BaseConv.BASES[i];
            boolean editing = (base == convBase);
            String text;
            if (raw.length() == 0) {
                text = "0";
            } else if (editing) {
                text = raw;                      // 正在编辑的那一行原样显示输入
            } else if (value != null) {
                text = BaseConv.display(value, base);
            } else {
                text = "—";
            }
            convValues[i].setText(text);
            convValues[i].setTextColor(editing ? COL_OK : COL_TEXT);
            convRows[i].setBackground(editing ? rounded(COL_ROW_ON, 10) : null);
        }

        // 当前进制下用不到的数字键灰掉
        for (int i = 0; i < convKeys.size(); i++) {
            Button b = convKeys.get(i);
            Object tag = b.getTag();
            boolean usable = true;
            if (tag instanceof Character) {
                usable = BaseConv.isValidDigit(((Character) tag).charValue(), convBase);
            }
            b.setEnabled(usable);
            b.setAlpha(usable ? 1f : 0.28f);
        }

        if (convHintView == null) return;
        if (err.length() > 0) {
            convHintView.setTextColor(COL_ERR);
            convHintView.setText(err);
            return;
        }
        convHintView.setTextColor(COL_SUB);
        int idx = BaseConv.indexOf(convBase);
        String name = (idx >= 0) ? BaseConv.NAMES[idx] : String.valueOf(convBase);
        if (value == null) {
            convHintView.setText("输入 " + name + " 进制的整数 ｜ 点击任意一行可切换输入进制");
        } else {
            convHintView.setText("输入 " + name + " ｜ 位数 " + BaseConv.bitLength(value)
                    + " 位 ｜ 仅支持整数");
        }
    }

    private View makeRow(String[] labels, String[] actions, int[] colors) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(MATCH, 0, 1f));
        for (int i = 0; i < labels.length; i++) {
            row.addView(makeButton(labels[i], actions[i], colors[i]));
        }
        return row;
    }

    private Button makeButton(String label, final String action, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(COL_TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.length() > 3 ? 14 : 18);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(buttonBg(color));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MATCH, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAction(action);
            }
        });
        if ("deg".equals(action)) {
            degBtn = b;
        }
        return b;
    }

    private Drawable buttonBg(int color) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(lighten(color, 0.24f), 12));
        s.addState(new int[]{}, rounded(color, 12));
        return s;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int lighten(int c, float f) {
        int r = Color.red(c) + Math.round((255 - Color.red(c)) * f);
        int g = Color.green(c) + Math.round((255 - Color.green(c)) * f);
        int b = Color.blue(c) + Math.round((255 - Color.blue(c)) * f);
        return Color.argb(255, Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
    // ------------------------------------------------------------------ 逻辑

    private void onAction(String a) {
        if (a == null) return;

        if (a.startsWith("cv:")) {          // 进制模式键盘
            onConvAction(a);
            return;
        }

        if (a.startsWith("ins:")) {
            String t = a.substring(4);
            boolean op = t.equals("+") || t.equals("-") || t.equals("*") || t.equals("/")
                    || t.equals("^") || t.equals("%") || t.equals("!")
                    || t.startsWith("^") || t.equals("mod");
            if (justEvaluated && !op) {
                input.setLength(0);            // 结果后输入新数字 -> 重新开始
            }
            justEvaluated = false;
            input.append(t);
            refresh();
            return;
        }

        if (a.equals("back")) {
            if (justEvaluated) {
                input.setLength(0);
            } else {
                backspace();
            }
            justEvaluated = false;
        } else if (a.equals("clear")) {
            input.setLength(0);
            justEvaluated = false;
            lastText = "";
        } else if (a.equals("deg")) {
            degree = !degree;
        } else if (a.equals("sign")) {
            toggleSign();
            justEvaluated = false;
        } else if (a.equals("equals")) {
            doEquals();
            return;
        }
        refresh();
    }

    /** 智能退格：整体删除函数名/常量等多字符词元 */
    private void backspace() {
        if (input.length() == 0) return;
        String s = input.toString();
        for (String t : MULTI_TOKENS) {
            if (s.endsWith(t)) {
                input.setLength(input.length() - t.length());
                return;
            }
        }
        input.setLength(input.length() - 1);
    }

    /** ± ：翻转末尾数字的符号 */
    private void toggleSign() {
        String s = input.toString();
        if (s.length() == 0) {
            input.append("-");
            return;
        }
        int end = s.length();
        int i = end;
        while (i > 0 && (Character.isDigit(s.charAt(i - 1)) || s.charAt(i - 1) == '.')) {
            i--;
        }
        if (i == end) {                       // 末尾不是数字，直接追加负号
            input.append("-");
            return;
        }
        if (i > 0 && s.charAt(i - 1) == '-') {
            boolean unary = (i - 1 == 0)
                    || "(+-*/^%".indexOf(s.charAt(i - 2)) >= 0;
            if (unary) {
                input.deleteCharAt(i - 1);
                return;
            }
        }
        input.insert(i, "-");
    }
    private void doEquals() {
        String s = input.toString();
        if (s.trim().length() == 0) return;
        try {
            double v = new Calc(s, degree).eval();
            input.setLength(0);
            input.append(fmtRaw(v));
            lastText = fmt(v);
            justEvaluated = true;
            refresh();
        } catch (Exception e) {
            justEvaluated = false;
            exprView.setText(pretty(s));
            resultView.setTextColor(COL_ERR);
            resultView.setText("错误");
        }
    }

    private void refresh() {
        if (degBtn != null) {
            degBtn.setText(degree ? "DEG" : "RAD");
        }
        String s = input.toString();

        if (justEvaluated) {
            exprView.setText("结果");
            resultView.setTextColor(COL_OK);
            resultView.setText(lastText);
            return;
        }

        exprView.setText(pretty(s));
        resultView.setTextColor(COL_TEXT);

        String r = "";
        if (s.trim().length() > 0) {
            try {
                r = fmt(new Calc(s, degree).eval());
            } catch (Exception e) {
                r = "";
            }
        }
        resultView.setText(r);
    }

    /** 显示用美化：* -> ×，/ -> ÷，- -> − */
    private String pretty(String s) {
        return s.replace("*", "×").replace("/", "÷").replace("-", "−");
    }

    /** 屏幕显示格式 */
    private String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "错误";
        if (v == Math.floor(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        double a = Math.abs(v);
        if (a >= 1e12 || a < 1e-6) {
            return new DecimalFormat("0.######E0", symbols()).format(v);
        }
        return new DecimalFormat("#.##########", symbols()).format(v);
    }

    /** 回填到表达式缓冲区的格式：不用指数记法，保证可再次解析 */
    private String fmtRaw(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        BigDecimal bd = new BigDecimal(v).round(new MathContext(12));
        String t = bd.stripTrailingZeros().toPlainString();
        if (t.equals("-0")) t = "0";
        return t;
    }

    private DecimalFormatSymbols symbols() {
        return DecimalFormatSymbols.getInstance(Locale.US);
    }
}
