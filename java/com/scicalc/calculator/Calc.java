package com.scicalc.calculator;

/**
 * 科学计算器表达式求值器（递归下降解析，无第三方依赖）。
 *
 * 支持的语法：
 *   数字           12、3.14、.5、科学计数 1.5E3
 *   常量           PI(π)、e
 *   双目运算符      +  -  *  /  ^(右结合)  mod
 *   后缀运算符      !(阶乘)  %(百分号)
 *   函数(需带括号)  sin cos tan asin acos atan ln log sqrt(√) exp abs
 *
 * 三角函数遵循当前角度制：DEG 时输入/输出为角度，RAD 时为弧度。
 */
class Calc {

    private final String s;
    private final boolean degree;
    private int pos;

    /** 必须以括号形式调用的函数名（"√" 是 sqrt 的显示形式） */
    private static final String[] FUNCS = {
            "asin", "acos", "atan",
            "sin", "cos", "tan",
            "log", "ln", "sqrt", "√", "exp", "abs"
    };

    Calc(String expression, boolean degree) {
        this.s = expression == null ? "" : expression;
        this.degree = degree;
    }

    double eval() {
        pos = 0;
        if (s.trim().isEmpty()) {
            throw new IllegalArgumentException("空表达式");
        }
        double v = expr();
        skip();
        if (pos < s.length()) {
            throw new IllegalArgumentException("无法识别的字符: " + s.charAt(pos));
        }
        if (Double.isNaN(v)) {
            throw new IllegalStateException("结果未定义");
        }
        return v;
    }

    // ---------------------------------------------------------------- 语法层

    private double expr() {
        double v = term();
        while (true) {
            skip();
            if (pos >= s.length()) break;
            char c = s.charAt(pos);
            if (c == '+') { pos++; v += term(); }
            else if (c == '-' || c == '−') { pos++; v -= term(); }
            else break;
        }
        return v;
    }

    private double term() {
        double v = unary();
        while (true) {
            skip();
            if (pos >= s.length()) break;
            char c = s.charAt(pos);
            if (c == '*' || c == '×') { pos++; v *= unary(); }
            else if (c == '/' || c == '÷') { pos++; v /= unary(); }
            else if (matchWord("mod")) { pos += 3; v = v % unary(); }
            else break;
        }
        return v;
    }

    private double unary() {
        skip();
        if (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '+') { pos++; return unary(); }
            if (c == '-' || c == '−') { pos++; return -unary(); }
        }
        return power();
    }

    private double power() {
        double base = postfix();
        skip();
        if (pos < s.length() && s.charAt(pos) == '^') {
            pos++;
            return Math.pow(base, unary());   // 右结合
        }
        return base;
    }

    private double postfix() {
        double v = primary();
        while (true) {
            skip();
            if (pos >= s.length()) break;
            char c = s.charAt(pos);
            if (c == '!') { pos++; v = factorial(v); }
            else if (c == '%') { pos++; v = v / 100.0; }
            else break;
        }
        return v;
    }
    private double primary() {
        skip();
        if (pos >= s.length()) {
            throw new IllegalArgumentException("表达式不完整");
        }
        char c = s.charAt(pos);

        if (c == '(') {                                  // 括号
            pos++;
            double v = expr();
            skip();
            if (pos >= s.length() || s.charAt(pos) != ')') {
                throw new IllegalArgumentException("缺少右括号");
            }
            pos++;
            return v;
        }

        for (String f : FUNCS) {                         // 函数调用（必须带括号）
            if (matchWord(f)) {
                pos += f.length();
                skip();
                if (pos >= s.length() || s.charAt(pos) != '(') {
                    throw new IllegalArgumentException(f + " 需要括号");
                }
                pos++;
                double arg = expr();
                skip();
                if (pos >= s.length() || s.charAt(pos) != ')') {
                    throw new IllegalArgumentException("缺少右括号");
                }
                pos++;
                return apply(f, arg);
            }
        }

        if (c == 'π') { pos++; return Math.PI; }
        if (c == 'e') { pos++; return Math.E; }

        if (Character.isDigit(c) || c == '.') {
            return number();
        }

        throw new IllegalArgumentException("无法识别的字符: " + c);
    }

    private void skip() {
        while (pos < s.length() && s.charAt(pos) == ' ') pos++;
    }

    /** 判断 s 在 pos 处是否以单词 w 开头，且 w 后面不是字母（避免误匹配 sin 与 sine）。
     *  注意：不能排除数字，否则 "10mod3" 里的 mod 会被误判为不匹配。 */
    private boolean matchWord(String w) {
        if (pos + w.length() > s.length()) return false;
        if (!s.regionMatches(pos, w, 0, w.length())) return false;
        int after = pos + w.length();
        if (after < s.length()) {
            char n = s.charAt(after);
            if (Character.isLetter(n) || n == '_') return false;
        }
        return true;
    }
    private double number() {
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
            pos++;
        }
        // 仅接受大写 E 的科学计数法，小写 e 保留给欧拉常数
        if (pos < s.length() && s.charAt(pos) == 'E') {
            pos++;
            if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
        }
        return Double.parseDouble(s.substring(start, pos));
    }

    private double apply(String f, double x) {
        if (f.equals("sin")) return Math.sin(angleToRad(x));
        if (f.equals("cos")) return Math.cos(angleToRad(x));
        if (f.equals("tan")) return Math.tan(angleToRad(x));
        if (f.equals("asin")) return radToAngle(Math.asin(x));
        if (f.equals("acos")) return radToAngle(Math.acos(x));
        if (f.equals("atan")) return radToAngle(Math.atan(x));
        if (f.equals("ln")) return Math.log(x);
        if (f.equals("log")) return Math.log10(x);
        if (f.equals("sqrt") || f.equals("√")) return Math.sqrt(x);
        if (f.equals("exp")) return Math.exp(x);
        if (f.equals("abs")) return Math.abs(x);
        throw new IllegalArgumentException("未知函数: " + f);
    }

    private double angleToRad(double x) {
        return degree ? Math.toRadians(x) : x;
    }

    private double radToAngle(double x) {
        return degree ? Math.toDegrees(x) : x;
    }

    private double factorial(double x) {
        if (x < 0 || x != Math.floor(x) || x > 170) {
            throw new IllegalArgumentException("阶乘仅支持 0~170 的整数");
        }
        double r = 1;
        for (int i = 2; i <= (int) x; i++) r *= i;
        return r;
    }
}
