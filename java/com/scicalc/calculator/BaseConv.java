package com.scicalc.calculator;

import java.math.BigInteger;
import java.util.Locale;

/**
 * 进制转换工具：在 2 / 8 / 10 / 16 进制之间互转「整数」。
 *
 * <p>刻意不依赖任何 Android API，也不依赖 {@link Calc}（那边是浮点表达式求值），
 * 只用 {@link BigInteger} 做整数运算。好处有两点：</p>
 * <ul>
 *   <li>任意长度不溢出（64 位十六进制、上百位十进制都能算）；</li>
 *   <li>可以用普通 javac/java 直接跑单元测试，见 tools/BaseConvTest.java。</li>
 * </ul>
 *
 * <p>约定：解析接受可选正负号与前后空白，字母不分大小写；
 * 输出一律大写、不带 {@code 0x/0b} 前缀，负数保留前导 {@code '-'}（符号绝对值记法）。</p>
 */
public final class BaseConv {

    public static final int BIN = 2;
    public static final int OCT = 8;
    public static final int DEC = 10;
    public static final int HEX = 16;

    /** 界面固定展示顺序：二进制、八进制、十进制、十六进制。 */
    public static final int[] BASES = { BIN, OCT, DEC, HEX };

    /** 与 {@link #BASES} 一一对应的行标签。 */
    public static final String[] NAMES = { "BIN", "OCT", "DEC", "HEX" };

    /** 单次输入允许的最大字符数，防止超长数字把界面撑爆。 */
    public static final int MAX_LEN = 64;

    private static final String DIGITS = "0123456789ABCDEF";

    private BaseConv() {
    }

    // ------------------------------------------------------------------ 单字符

    /** 数字字符 -> 数值（0~15）；不是十六进制字符时返回 -1。大小写均可。 */
    public static int digitValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }

    /** 字符 c 在 base 进制下是否是合法数字。 */
    public static boolean isValidDigit(char c, int base) {
        int d = digitValue(c);
        return d >= 0 && d < base;
    }

    /** base 进制的全部可用字符，按数值升序。例如 16 -> "0123456789ABCDEF"。 */
    public static String digitsOf(int base) {
        if (base < BIN || base > HEX) return "";
        return DIGITS.substring(0, base);
    }

    /** base 在 {@link #BASES} 里的下标；不是 2/8/10/16 时返回 -1。 */
    public static int indexOf(int base) {
        for (int i = 0; i < BASES.length; i++) {
            if (BASES[i] == base) return i;
        }
        return -1;
    }

    /** 丢掉 base 进制下不合法的字符，并把合法字母统一成大写。 */
    public static String filter(String s, int base) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isValidDigit(c, base)) sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 解析 / 输出

    /**
     * 按 base 把字符串解析成整数。
     *
     * <p>规则：允许前后空白、允许一个可选的正负号、字母大小写均可、
     * 允许多余前导零（{@code "00001111"} 等价于 {@code "1111"}）。</p>
     *
     * @throws IllegalArgumentException 输入为空、只有一个符号、或含有该进制下的非法字符
     */
    public static BigInteger parse(String s, int base) {
        if (base < BIN || base > HEX) {
            throw new IllegalArgumentException("不支持的进制 " + base);
        }
        String t = (s == null) ? "" : s.trim();

        boolean negative = false;
        if (t.startsWith("-")) {
            negative = true;
            t = t.substring(1);
        } else if (t.startsWith("+")) {
            t = t.substring(1);
        }
        if (t.length() == 0) {
            throw new IllegalArgumentException("请输入 " + base + " 进制的整数");
        }

        BigInteger acc = BigInteger.ZERO;
        BigInteger radix = BigInteger.valueOf(base);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            int d = digitValue(c);
            if (d < 0 || d >= base) {
                throw new IllegalArgumentException("字符 '" + c + "' 不是 " + base + " 进制数字");
            }
            acc = acc.multiply(radix).add(BigInteger.valueOf(d));
        }
        return negative ? acc.negate() : acc;
    }

    /** 整数 -> base 进制字符串（大写、无前缀）；负数带前导 {@code '-'}。 */
    public static String format(BigInteger v, int base) {
        if (v == null) return "";
        if (v.signum() < 0) {
            return "-" + v.abs().toString(base).toUpperCase(Locale.US);
        }
        return v.toString(base).toUpperCase(Locale.US);
    }

    /** 便捷方法：把 s 从 fromBase 直接转成 toBase 的字符串。 */
    public static String convert(String s, int fromBase, int toBase) {
        return format(parse(s, fromBase), toBase);
    }

    // ------------------------------------------------------------------ 展示美化

    /**
     * 从右往左每 size 位插一个分隔符，用于长数字断读。
     * 前导负号不参与分组，长度不足时原样返回。
     */
    public static String group(String s, int size, char sep) {
        if (s == null) return "";
        String sign = "";
        String body = s;
        if (body.startsWith("-")) {
            sign = "-";
            body = body.substring(1);
        }
        if (size <= 1 || body.length() <= size) return s;

        StringBuilder sb = new StringBuilder(sign);
        int head = body.length() % size;
        if (head == 0) head = size;
        sb.append(body, 0, head);
        for (int i = head; i < body.length(); i += size) {
            sb.append(sep).append(body, i, Math.min(i + size, body.length()));
        }
        return sb.toString();
    }

    /**
     * 界面展示用的字符串：二进制每 4 位、十六进制每 4 位、八进制每 3 位加空格分组，
     * 十进制保持原样（避免和千分位逗号混淆）。
     */
    public static String display(BigInteger v, int base) {
        String s = format(v, base);
        switch (base) {
            case BIN: return group(s, 4, ' ');
            case OCT: return group(s, 3, ' ');
            case HEX: return group(s, 4, ' ');
            default:  return s;
        }
    }

    /** 有效二进制位数；0 记为 1 位。用于界面提示当前值有多宽。 */
    public static int bitLength(BigInteger v) {
        if (v == null) return 0;
        return v.signum() == 0 ? 1 : v.abs().bitLength();
    }
}
