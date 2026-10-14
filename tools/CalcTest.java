package com.scicalc.calculator;

/** Calc 解析器的独立验证程序（纯 Java，不依赖 Android），用 javac/java 直接跑。 */
public class CalcTest {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        chk("1+2*3", true, 7);
        chk("(1+2)*3", true, 9);
        chk("2^3^2", true, 512);            // 右结合: 2^(3^2)
        chk("2^-1", true, 0.5);
        chk("-3+5", true, 2);
        chk("10mod3", true, 1);
        chk("10%3", false, -999);           // % 是百分号 -> 0.1 后跟 3 属于非法，应报错
        chk("50%", true, 0.5);
        chk("5!", true, 120);
        chk("0!", true, 1);
        chk("sin(30)", true, 0.5);          // DEG
        chk("sin(30)", false, Math.sin(30));// RAD
        chk("cos(60)", true, 0.5);
        chk("atan(1)", true, 45);
        chk("sqrt(16)", true, 4);
        chk("\u221a(81)", true, 9);
        chk("ln(e)", true, 1);
        chk("log(1000)", true, 3);
        chk("\u03c0", true, Math.PI);
        chk("3.5E2", true, 350);
        chk("abs(0-7)", true, 7);
        chk("1/3", true, 1.0 / 3.0);
        chk("2+", true, -999);              // 不完整 -> 报错
        chk("(1+2", true, -999);            // 缺右括号 -> 报错
        chk("sin(1,2)", true, -999);        // 非法字符 -> 报错

        System.out.println("----------------------------------");
        System.out.println("PASS = " + pass + " , FAIL = " + fail);
        if (fail > 0) System.exit(1);
    }

    private static void chk(String expr, boolean deg, double expect) {
        String got;
        boolean ok;
        try {
            double v = new Calc(expr, deg).eval();
            got = String.valueOf(v);
            ok = Math.abs(v - expect) < 1e-9;
        } catch (Exception e) {
            got = "ERR(" + e.getMessage() + ")";
            ok = (expect == -999);          // -999 表示期望报错
        }
        if (ok) pass++; else fail++;
        System.out.printf("%-6s %-10s [%s] -> %s%s%n",
                ok ? "OK" : "FAIL", expr, deg ? "DEG" : "RAD", got,
                ok ? "" : "   expected=" + String.valueOf(expect));
    }
}
