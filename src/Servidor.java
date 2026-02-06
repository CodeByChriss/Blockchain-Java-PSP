import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class Servidor {

    private static List<Block> blockchain = new ArrayList<>();
    private static final double TEMP_LIMITE = 50.0;

    public static void main(String[] args) {

        // Bloque Génesis
        blockchain.add(new Block("Genesis Block", "0"));

        try (ServerSocket serverSocket = new ServerSocket(6000)) {
            System.out.println("Servidor de Monitoreo listo en puerto 6000...");

            while (true) {
                try (Socket clientSocket = serverSocket.accept(); BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                    String data = in.readLine(); // Formato esperado: "NOMBRE_SENSOR;TEMP:valor"
                    System.out.println(data);
                    String sensorId = data.split(";")[0];
                    String dataTemp = data.split(";")[1];
                    double temp = Double.parseDouble(dataTemp.split(":")[1]);

                    if (!isChainValid()) {
                        System.err.println("ERROR CRÍTICO: La base de datos Blockchain ha sido manipulada.");
                        out.println("ERROR:Integridad de red comprometida.");
                        break; // Detener el servidor por seguridad
                    }

                    // 1. Guardar en la base de datos SQL remota (IP: 192.168.20.118)
                    int idGenerado = DatabaseService.guardarLectura(sensorId, temp);

                    if (idGenerado != -1) {
                        // 2. Crear el bloque en la Blockchain
                        // Usamos el hash previo de nuestra lista en memoria
                        String prevHash = blockchain.get(blockchain.size() - 1).hash;

                        // El 'data' del bloque será el hash de integridad de la BD SQL
                        String dataHash = generarDataHash(sensorId, temp, String.valueOf(idGenerado));
                        Block nuevoBloque = new Block(dataHash, prevHash);
                        blockchain.add(nuevoBloque);

                        // 3. Volver a la SQL para guardar la referencia del bloque (el sello final)
                        DatabaseService.vincularConBlockchain(idGenerado, nuevoBloque.hash);

                        System.out.println("Bloque añadido. Hash: " + nuevoBloque.hash);
                        out.println("OK:Registro guardado en Blockchain");

                        System.out.println("Sincronización completa: SQL (ID " + idGenerado + ") <-> Blockchain (Hash " + nuevoBloque.hash.substring(0, 8) + "...)");
                    }

                    if (temp > TEMP_LIMITE) {
                        System.err.println("CRÍTICO: Temperatura " + temp + "°C excede el límite.");
                        out.println("SISTEMA_APAGADO");
                        System.out.println("Simulando apagado de seguridad del servidor...");
                        break; // Cerramos el servidor
                    }

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Boolean isChainValid() {
        Block currentBlock;
        Block previousBlock;

        // Recorremos la cadena desde el segundo bloque (índice 1)
        for (int i = 1; i < blockchain.size(); i++) {
            currentBlock = blockchain.get(i);
            previousBlock = blockchain.get(i - 1);

            // 1. Validar que el hash del bloque actual sea correcto
            if (!currentBlock.hash.equals(currentBlock.calculateHash())) {
                System.err.println("¡ALERTA! El hash del bloque " + i + " no coincide con sus datos.");
                return false;
            }

            // 2. Validar que el bloque actual apunte al hash del bloque anterior
            if (!currentBlock.previousHash.equals(previousBlock.hash)) {
                System.err.println("¡ALERTA! El bloque " + i + " no está bien enlazado con el bloque " + (i - 1));
                return false;
            }
        }
        return true;
    }

    public static String generarDataHash(String sensorId, double temp, String timestamp) {
        // Concatenamos los campos clave del registro SQL
        String registroCompleto = sensorId + Double.toString(temp) + timestamp;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(registroCompleto.getBytes(StandardCharsets.UTF_8));

            // Convertimos a Hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString(); // Este es el "sello digital" del dato SQL
        } catch (Exception e) {
            return null;
        }
    }

}