import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class Block {

    private String id;
    private long timestamp;
    private Double temp;
    private String hash;
    private String previousHash;

    public Block(String id, Long timestamp, Double temp, String previousHash) {
        this.id = id;
        this.temp = temp;
        this.timestamp = timestamp;
        this.previousHash = previousHash;
        this.hash = calculateHash();
    }

    public Block(String id, Long timestamp, Double temp, String previousHash, String hash) {
        this.id = id;
        this.temp = temp;
        this.timestamp = timestamp;
        this.previousHash = previousHash;
        this.hash = hash;
    }

    public String calculateHash() {
        String registroCompleto = id + timestamp + String.format("%.2f", temp) + previousHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(registroCompleto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public String getHash(){
        return this.hash;
    }

    public String getPreviousHash(){
        return this.previousHash;
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Double getTemp() {
        return temp;
    }
}