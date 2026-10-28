import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Offline export of the same node IDs/readings used by KanaKanjiEngine. JDK 17 only. */
public class ExportBundledEmoji {
    record Trie(BitSet bits, BitSet leaves, char[] labels, int[] terms) {
        static Trie read(Path path, boolean reading) throws Exception {
            try (var in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
                return new Trie((BitSet) in.readObject(), (BitSet) in.readObject(),
                    (char[]) in.readObject(), reading ? (int[]) in.readObject() : null);
            }
        }
        int rank1(int pos) { return bits.get(0, pos + 1).cardinality(); }
        int select1(int n) {
            int pos = -1;
            while (n-- > 0) pos = bits.nextSetBit(pos + 1);
            return pos;
        }
        String word(int node) {
            var result = new StringBuilder();
            while (node != 0) {
                int id = rank1(node);
                if (id > 1) result.append(labels[id]);
                node = select1(node + 1 - id);
            }
            // Reverse UTF-16 code units, just as the production Char list (not StringBuilder.reverse).
            char[] chars = new char[result.length()];
            for (int i = 0; i < chars.length; i++) chars[i] = result.charAt(chars.length - i - 1);
            return new String(chars);
        }
    }
    static int select0(BitSet bits, int n) {
        int pos = -1;
        while (n-- > 0) pos = bits.nextClearBit(pos + 1);
        return pos;
    }
    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]);
        Trie tango = Trie.read(assets.resolve("tango_emoji.dat"), false);
        Trie yomi = Trie.read(assets.resolve("yomi_emoji.dat"), true);
        int[] nodes;
        BitSet tokenBits;
        try (var in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(assets.resolve("token_emoji.dat"))))) {
            in.readObject(); in.readObject(); nodes = (int[]) in.readObject(); tokenBits = (BitSet) in.readObject();
        }
        var readings = new TreeMap<String, SortedSet<String>>();
        for (int node : nodes) readings.computeIfAbsent(tango.word(node), k -> new TreeSet<>());
        for (int leaf = yomi.leaves.nextSetBit(0); leaf >= 0; leaf = yomi.leaves.nextSetBit(leaf + 1)) {
            int term = yomi.terms[yomi.leaves.get(0, leaf + 1).cardinality() - 1];
            int start = tokenBits.get(0, select0(tokenBits, term) + 1).cardinality();
            int end = tokenBits.get(0, select0(tokenBits, term + 1) + 1).cardinality();
            String reading = yomi.word(leaf);
            for (int i = start; i < end; i++) readings.get(tango.word(nodes[i])).add(reading);
        }
        try (var out = new PrintWriter(Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8))) {
            for (var entry : readings.entrySet()) out.println(entry.getKey() + "\t" + String.join("|", entry.getValue()));
        }
        System.out.println("Exported " + readings.size() + " bundled emoji");
    }
}
