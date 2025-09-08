import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class SecretSharing {

    static class Share {
        int x;
        BigInteger y;
        Share(int x, BigInteger y) { this.x = x; this.y = y; }
    }

    static class Fraction {
        BigInteger num, den;
        Fraction(BigInteger n, BigInteger d) {
            if (d.signum() == 0) throw new ArithmeticException("Denominator = 0");
            if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
            BigInteger g = n.gcd(d);
            num = n.divide(g);
            den = d.divide(g);
        }
        static Fraction of(BigInteger n) { return new Fraction(n, BigInteger.ONE); }
        Fraction add(Fraction o) {
            return new Fraction(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den));
        }
        Fraction mul(Fraction o) {
            return new Fraction(num.multiply(o.num), den.multiply(o.den));
        }
        BigInteger toBigIntegerExact() {
            if (!den.equals(BigInteger.ONE))
                throw new ArithmeticException("Result not an integer: " + num + "/" + den);
            return num;
        }
    }

    static List<Share> parseShares(String json) {
        List<Share> shares = new ArrayList<>();
        Pattern p = Pattern.compile(
            "\"(\\d+)\"\\s*:\\s*\\{\\s*\"base\"\\s*:\\s*\"(\\d+)\"\\s*,\\s*\"value\"\\s*:\\s*\"([0-9a-zA-Z]+)\"\\s*\\}",
            Pattern.DOTALL);
        Matcher m = p.matcher(json);
        while (m.find()) {
            int x = Integer.parseInt(m.group(1));
            int base = Integer.parseInt(m.group(2));
            String val = m.group(3).toLowerCase(Locale.ROOT);
            BigInteger y = new BigInteger(val, base);
            shares.add(new Share(x, y));
        }
        shares.sort(Comparator.comparingInt(s -> s.x));
        return shares;
    }

    static int parseK(String json) {
        int idx = json.indexOf("\"k\"");
        if (idx == -1) throw new IllegalArgumentException("Missing k in JSON");
        String sub = json.substring(idx, Math.min(idx + 50, json.length()));
        Matcher mk = Pattern.compile(":\\s*(\\d+)").matcher(sub);
        if (mk.find()) {
            return Integer.parseInt(mk.group(1));
        }
        throw new IllegalArgumentException("Invalid k format");
    }

    static BigInteger reconstructC(List<Share> shares, int k) {
        if (shares.size() < k)
            throw new IllegalArgumentException("Not enough shares: have " + shares.size() + " need " + k);
        Fraction sum = Fraction.of(BigInteger.ZERO);
        List<Share> pts = shares.subList(0, k);
        for (int i = 0; i < k; i++) {
            Share si = pts.get(i);
            Fraction term = Fraction.of(si.y);
            for (int j = 0; j < k; j++) {
                if (i == j) continue;
                Share sj = pts.get(j);
                term = term.mul(new Fraction(
                        BigInteger.valueOf(-sj.x),
                        BigInteger.valueOf(si.x - sj.x)));
            }
            sum = sum.add(term);
        }
        return sum.toBigIntegerExact();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java SecretSharing <file1.json> [file2.json ...]");
            System.exit(1);
        }

        for (String fname : args) {
            try {
                String json = Files.readString(Paths.get(fname));
                List<Share> shares = parseShares(json);
                int k = parseK(json);
                BigInteger secret = reconstructC(shares, k);
                System.out.println(fname + ": " + secret.toString());
            } catch (IOException ioe) {
                System.err.println(fname + ": I/O error - " + ioe.getMessage());
            } catch (IllegalArgumentException | ArithmeticException ex) {
                System.err.println(fname + ": Error - " + ex.getMessage());
            } catch (Exception ex) {
                System.err.println(fname + ": Unexpected error - " + ex.getMessage());
            }
        }
    }
}
