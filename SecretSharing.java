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

    // Parse shares from JSON
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

    // Parse k from JSON
    static int parseK(String json) {
        int idx = json.indexOf("\"k\"");
        if (idx == -1) throw new IllegalArgumentException("Missing k in JSON");
        String sub = json.substring(idx, Math.min(idx + 50, json.length()));
        Matcher mk = Pattern.compile(":\\s*(\\d+)").matcher(sub);
        if (mk.find()) return Integer.parseInt(mk.group(1));
        throw new IllegalArgumentException("Invalid k format");
    }

    // Find a prime larger than the largest share
    static BigInteger findPrimeLargerThan(BigInteger maxShare) {
        // Add some safety margin
        BigInteger candidate = maxShare.add(BigInteger.valueOf(1000));
        if (!candidate.isProbablePrime(100)) {
            candidate = candidate.nextProbablePrime();
        }
        return candidate;
    }

    // Modular Lagrange interpolation
    static BigInteger reconstructSecretMod(List<Share> shares, int k, BigInteger mod) {
        if (shares.size() < k)
            throw new IllegalArgumentException("Not enough shares");
        BigInteger sum = BigInteger.ZERO;
        List<Share> pts = shares.subList(0, k);

        for (int i = 0; i < k; i++) {
            Share si = pts.get(i);
            BigInteger term = si.y.mod(mod);

            for (int j = 0; j < k; j++) {
                if (i == j) continue;
                Share sj = pts.get(j);
                BigInteger numerator = BigInteger.valueOf(-sj.x).mod(mod);
                BigInteger denominator = BigInteger.valueOf(si.x - sj.x).mod(mod);
                term = term.multiply(numerator.multiply(denominator.modInverse(mod)).mod(mod)).mod(mod);
            }

            sum = sum.add(term).mod(mod);
        }

        return sum;
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

                // Find largest share
                BigInteger maxShare = shares.stream().map(s -> s.y).max(BigInteger::compareTo).orElse(BigInteger.ONE);
                BigInteger prime = findPrimeLargerThan(maxShare);

                BigInteger secret = reconstructSecretMod(shares, k, prime);
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
