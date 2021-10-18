import java.io.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;


public class Vigenere {
    private static int alphabetSize = ('я' - 'а' + 1);
    private static double[] charProbabilities = new double[alphabetSize];
    private static Set<String> dictionary = new HashSet<>();
    private static String root = "src\\main\\resources\\";

    private static void init() throws IOException {
        // Build the dictionary.
        for (String word : readAndPrepare("src\\main\\resources\\sample.txt").split(" ")) {
            dictionary.add(word);
        }

        // Calculate letter occurrence frequencies.
        int count = 0;
        for (char c : readAndPrepare("src\\main\\resources\\sample.txt").toCharArray()) {
            if (c == ' ') {
                continue;
            }
            charProbabilities[c - 'а']++;
            count++;
        }
        for (int i = 0; i < alphabetSize; i++) {
            charProbabilities[i] /= count;
        }
    }

    private static String readFromFile(String path) throws IOException {
        try(BufferedReader br = new BufferedReader(new FileReader(path))) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line + " ");
                line = br.readLine();
            }

            return sb.toString();
        }
    }

    private static String readAndPrepare(String path) throws IOException {
        return prepare(readFromFile(path));
    }

    private static void writeToFile(String path, String data) throws IOException {
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write(data);
        }
    }

    public static void main(String[] args) throws IOException {
        init();

        while(true) {
            try {
                Scanner scanner = new Scanner(System.in);
                String[] input = scanner.nextLine().split(" ");

                if (input[0].equals("шифр")) {
                    if (input.length < 4) { throw new IOException(); }
                    writeToFile(getPath(input[2]), encrypt(readAndPrepare(getPath(input[1])), input[3]));
                } else if (input[0].equals("дешифр")) {
                    if (input.length < 4) { throw new IOException(); }
                    writeToFile(getPath(input[2]), decrypt(readAndPrepare(getPath(input[1])), input[3]));
                } else if (input[0].equals("взлом")) {
                    if (input.length < 3) { throw new IOException(); }
                    String key = tryGetKey(readAndPrepare(getPath(input[1])));
                    writeToFile(getPath(input[2]), decrypt(readAndPrepare(getPath(input[1])), key));
                    System.out.println("Ключ: " + key);
                }
            } catch (IOException e) {
                System.out.println("Ошибка");
            }
        }
    }

    public static String getPath(String fileName) {
        return (root + fileName + ".txt");
    }

    public static String prepare(String text) {
        return text.toLowerCase(Locale.ROOT).chars()
                .map(c -> c == 'ё' ? 'е' : c)
                .filter(c -> c >= 'а' && c <= 'я' || c == ' ')
                .mapToObj(c -> (char) c).map(Object::toString).collect(Collectors.joining());
    }

    public static String encrypt(String text, String key) {
        char[] data = text.toCharArray();
        char[] keyChars = key.toCharArray();
        int keyIter = 0;

        for (int i = 0; i < data.length; i++) {
            if (data[i] == ' ') {
                continue;
            }
            data[i] = (char) ('а' + ((data[i] + keyChars[keyIter%keyChars.length] - 2*'а') % alphabetSize));
            keyIter++;
        }

        return String.valueOf(data).replaceAll(" ", "");
    }

    public static String decrypt(String text, String key) {
        char[] data = text.toCharArray();
        char[] keyChars = key.toCharArray();
        int keyIter = 0;

        for (int i = 0; i < data.length; i++) {
            data[i] = (char) ('а' + ((32 + data[i] - keyChars[keyIter%keyChars.length]) % ('я' - 'а' + 1))) ;
            keyIter++;
        }

        return refine(String.valueOf(data));
    }

    public static String crack(String text) {
        return decrypt(text, tryGetKey(text));
    }

    public static String tryGetKey(String text) {
        int keyLength = computeKeyLength(text);
        char[] data = text.replaceAll(" ", "").toCharArray();
        char[] key = new char[keyLength];

        for (int series = 0; series < keyLength; series++) {
            int count = 0;
            double[] chars = new double[alphabetSize];
            for (int j = series; j < data.length; j += keyLength) {
                chars[data[j] - 'а']++;
                count++;
            }

            for (int j = 0; j < alphabetSize; j++) {
                chars[j] /= count;
            }

            key[series] = (char) (computeDisplacement(chars) + 'а');
        }

        return String.valueOf(key);
    }

    private static int computeDisplacement(double[] charFrequencies) {
        int disp = -1;
        double minDistance = alphabetSize;

        for (int shift = 0; shift < alphabetSize; shift++) {
            double distance = 0.0;
            for (int freq = 0; freq < alphabetSize; freq++) {
                distance += Math.abs(charFrequencies[(alphabetSize + freq + shift) % alphabetSize] - charProbabilities[freq]);
            }

            if (distance < minDistance) {
                minDistance = distance;
                disp = shift;
            }
        }

        return disp;
    }

    private static int computeKeyLength(String text) {
        int keyLength = 0;
        double index = 0;

        for (int t = 1; t < text.length(); t++) {
            int[] count = new int[alphabetSize];

            char[] data = text.replaceAll(" ", "").toCharArray();
            for (int i = 0; i < data.length; i += t) {
                if (data[i] == ' ') {
                    continue;
                }
                count[data[i] - 'а']++;
            }

            double newIndex = 0;
            int groupSize = data.length / t;
            for (int i = 0; i < alphabetSize; i++) {
                newIndex += count[i] * (count[i] - 1) / (double) (groupSize * (groupSize - 1));
            }

            keyLength = t;
            if (index != 0 && newIndex > 1.16 * index) {
                break;
            }
            index = newIndex;
        }

        return keyLength;
    }

    private static String refine(String rawDecrypted) {
        StringBuilder text = new StringBuilder();
        int start = 0;
        while (start < rawDecrypted.length()) {
            String best = rawDecrypted.substring(start, start+1);

            for (int i = 1; i < rawDecrypted.length(); i++) {
                int len = i + 1;
                if (start + len > rawDecrypted.length()) {
                    start += best.length();
                    break;
                }

                String newWord = rawDecrypted.substring(start, start+len);
                if (dictionary.stream()
                        .anyMatch(s -> s.length() >= newWord.length()
                                && s.substring(0, len).equals(newWord))) {
                    if (dictionary.contains(newWord)) {
                        best = newWord;
                    }
                } else {
                    start += best.length();
                    break;
                }
            }

            text.append(best);
            text.append(' ');
        }

        return text.toString();
    }
}
