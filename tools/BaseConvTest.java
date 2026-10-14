package com.scicalc.calculator;

import java.math.BigInteger;

/**
 * BaseConv 的独立验证程序（纯 Java，不依赖 Android），用 javac/java 直接跑。
 *
 * <pre>
 *   javac -encoding UTF-8 -d build\testout -sourcepath java tools\BaseConvTest.java
 *   java -cp build\testout com.scicalc.calculator.BaseConvTest
 * </pre>
 */
public class BaseConvTest {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {

        // ---------------- 基本互转：255 在四种进制下的样子 ----------------
        cv("255", BaseConv.DEC, BaseConv.HEX, "FF");
        cv("255", BaseConv.DEC, BaseConv.BIN, "11111111");
        cv("255", BaseConv.DEC, BaseConv.OCT, "377");
        cv("FF", BaseConv.HEX, BaseConv.DEC, "255");
        cv("FF", BaseConv.HEX, BaseConv.BIN, "11111111");
        cv("FF", BaseConv.HEX, BaseConv.OCT, "377");
        cv("11111111", BaseConv.BIN, BaseConv.DEC, "255");
        cv("11111111", BaseConv.BIN, BaseConv.HEX, "FF");
        cv("11111111", BaseConv.BIN, BaseConv.OCT, "377");
        cv("377", BaseConv.OCT, BaseConv.DEC, "255");
        cv("377", BaseConv.OCT, BaseConv.BIN, "11111111");

        // ---------------- 同进制恒等 ----------------
        cv("255", BaseConv.DEC, BaseConv.DEC, "255");
        cv("0", BaseConv.DEC, BaseConv.HEX, "0");
        cv("0", BaseConv.BIN, BaseConv.DEC, "0");

        // ---------------- 负数（符号 + 绝对值记法） ----------------
        cv("-255", BaseConv.DEC, BaseConv.HEX, "-FF");
        cv("-255", BaseConv.DEC, BaseConv.BIN, "-11111111");
        cv("-FF", BaseConv.HEX, BaseConv.DEC, "-255");
        cv("-1010", BaseConv.BIN, BaseConv.DEC, "-10");

        // ---------------- 大小写、空白、正号、前导零 ----------------
        cv("ff", BaseConv.HEX, BaseConv.DEC, "255");
        cv(" 1010 ", BaseConv.BIN, BaseConv.DEC, "10");
        cv("+1010", BaseConv.BIN, BaseConv.DEC, "10");
        cv("00001111", BaseConv.BIN, BaseConv.DEC, "15");
        cv("0000FF", BaseConv.HEX, BaseConv.DEC, "255");

        // ---------------- 边界：32 位 / 64 位 / 超出 long ----------------
        cv("FFFFFFFF", BaseConv.HEX, BaseConv.DEC, "4294967295");
        cv("7FFFFFFF", BaseConv.HEX, BaseConv.DEC, "2147483647");
        cv("7FFFFFFFFFFFFFFF", BaseConv.HEX, BaseConv.DEC, "9223372036854775807");
        // 下面这个超出 long 范围，必须靠 BigInteger 才不溢出
        cv("FFFFFFFFFFFFFFFF", BaseConv.HEX, BaseConv.DEC, "18446744073709551615");
        cv("18446744073709551615", BaseConv.DEC, BaseConv.HEX, "FFFFFFFFFFFFFFFF");
        cv("123456789012345678901234567890", BaseConv.DEC, BaseConv.HEX,
           "18EE90FF6C373E0EE4E3F0AD2");
        // ---------------- 往返一致性：四种进制各转一圈 ----------------
        roundTrip("123456789", BaseConv.DEC);
        roundTrip("-987654321", BaseConv.DEC);
        roundTrip("0", BaseConv.DEC);
        roundTrip("65535", BaseConv.HEX);
        roundTrip("7777777777", BaseConv.OCT);

        // ---------------- 非法输入必须报错 ----------------
        bad("", BaseConv.DEC);              // 空串
        bad("   ", BaseConv.DEC);           // 全空白
        bad("-", BaseConv.DEC);             // 只有符号
        bad("+", BaseConv.BIN);
        bad("12", BaseConv.BIN);            // 2 不是二进制数字
        bad("8", BaseConv.OCT);             // 8 不是八进制数字
        bad("9", BaseConv.OCT);
        bad("G", BaseConv.HEX);             // 超出十六进制
        bad("1F", BaseConv.DEC);            // 十进制里出现字母
        bad("1 0", BaseConv.DEC);           // 中间夹空格
        bad("1.5", BaseConv.DEC);           // 小数不支持（转换只处理整数）
        bad("0x1F", BaseConv.HEX);          // 不接受 0x 前缀

        // ---------------- 单字符判定 ----------------
        yes(BaseConv.isValidDigit('F', BaseConv.HEX));
        yes(BaseConv.isValidDigit('f', BaseConv.HEX));
        no(BaseConv.isValidDigit('F', BaseConv.DEC));
        yes(BaseConv.isValidDigit('7', BaseConv.OCT));
        no(BaseConv.isValidDigit('8', BaseConv.OCT));
        yes(BaseConv.isValidDigit('1', BaseConv.BIN));
        no(BaseConv.isValidDigit('2', BaseConv.BIN));

        // ---------------- 字符集与过滤 ----------------
        eq("0123456789ABCDEF", BaseConv.digitsOf(BaseConv.HEX));
        eq("01", BaseConv.digitsOf(BaseConv.BIN));
        eq("01234567", BaseConv.digitsOf(BaseConv.OCT));
        eq("123", BaseConv.filter("1G2Z3", BaseConv.HEX));
        eq("FF", BaseConv.filter("f f", BaseConv.HEX));

        // ---------------- 分组显示 ----------------
        eq("1111 1111", BaseConv.group("11111111", 4, ' '));
        eq("1 1111 1111", BaseConv.group("111111111", 4, ' '));
        eq("F FFFF", BaseConv.group("FFFFF", 4, ' '));
        eq("-1111 1111", BaseConv.group("-11111111", 4, ' '));
        eq("FF", BaseConv.group("FF", 4, ' '));
        eq("1111 1111", BaseConv.display(new BigInteger("255"), BaseConv.BIN));
        eq("377", BaseConv.display(new BigInteger("255"), BaseConv.OCT));
        eq("255", BaseConv.display(new BigInteger("255"), BaseConv.DEC));
        eq("FFFF", BaseConv.display(new BigInteger("65535"), BaseConv.HEX));
        eq("-FF", BaseConv.display(new BigInteger("-255"), BaseConv.HEX));

        // ---------------- 位数提示 ----------------
        eq(1, BaseConv.bitLength(new BigInteger("0")));
        eq(1, BaseConv.bitLength(new BigInteger("1")));
        eq(8, BaseConv.bitLength(new BigInteger("255")));
        eq(9, BaseConv.bitLength(new BigInteger("256")));
        eq(9, BaseConv.bitLength(new BigInteger("-256")));

        System.out.println("----------------------------------");
        System.out.println("PASS = " + pass + " , FAIL = " + fail);
        if (fail > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ 断言

    /** 跨进制转换：convert(in, from, to) 必须等于 expect。 */
    private static void cv(String in, int from, int to, String expect) {
        String got;
        boolean ok;
        try {
            got = BaseConv.convert(in, from, to);
            ok = got.equals(expect);
        } catch (Exception e) {
            got = "ERR(" + e.getMessage() + ")";
            ok = false;
        }
        report(ok, "[" + from + "->" + to + "] '" + in + "'", got, expect);
    }

    /** 期望解析失败。 */
    private static void bad(String in, int from) {
        String got;
        boolean ok;
        try {
            BigInteger v = BaseConv.parse(in, from);
            got = "no-error:" + v;
            ok = false;
        } catch (IllegalArgumentException e) {
            got = "ERR";
            ok = true;
        }
        report(ok, "reject [" + from + "] '" + in + "'", got, "ERR");
    }

    /** 依次经过 2/8/10/16 进制，最后必须回到原值。 */
    private static void roundTrip(String in, int from) {
        String cur = in;
        int base = from;
        for (int b : BaseConv.BASES) {
            cur = BaseConv.convert(cur, base, b);
            base = b;
        }
        cur = BaseConv.convert(cur, base, from);

        boolean ok;
        String got = cur;
        try {
            ok = BaseConv.parse(cur, from).equals(BaseConv.parse(in, from));
        } catch (Exception e) {
            ok = false;
            got = "ERR(" + e.getMessage() + ")";
        }
        report(ok, "roundtrip [" + from + "] '" + in + "'", got, in);
    }

    private static void yes(boolean cond) {
        report(cond, "isValidDigit -> true", String.valueOf(cond), "true");
    }

    private static void no(boolean cond) {
        report(!cond, "isValidDigit -> false", String.valueOf(cond), "false");
    }

    private static void eq(String expect, String got) {
        report(expect.equals(got), "equals", "'" + got + "'", "'" + expect + "'");
    }

    private static void eq(int expect, int got) {
        report(expect == got, "equals(int)", String.valueOf(got), String.valueOf(expect));
    }

    private static void report(boolean ok, String what, String got, String expect) {
        if (ok) pass++; else fail++;
        System.out.printf("%-5s %-40s -> %s%s%n",
                ok ? "OK" : "FAIL", what, got,
                ok ? "" : "   expected=" + expect);
    }
}
