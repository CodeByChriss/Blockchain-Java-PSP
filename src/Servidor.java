import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class Servidor {

    private static List<Block> blockchain = new ArrayList<>();
    private static final double TEMP_LIMITE = 50.0;

    public static void main(String[] args) {
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
                    long timestamp = System.currentTimeMillis();
                    int idGenerado = DatabaseService.guardarLectura(sensorId, temp, timestamp);

                    if (idGenerado != -1) {
                        // 2. Crear el bloque en la Blockchain
                        // Usamos el hash previo de nuestra lista en memoria
                        String prevHash = "";
                        try {
                            prevHash = blockchain.getLast().getHash();
                        } catch (NoSuchElementException _) {
                        }

                        // El 'data' del bloque será el hash de integridad de la BD SQL
                        Block nuevoBloque = new Block(sensorId, timestamp, temp, prevHash);
                        blockchain.add(nuevoBloque);

                        // 3. Volver a la SQL para guardar la referencia del bloque (el sello final)
                        DatabaseService.vincularConBlockchain(idGenerado, nuevoBloque.getHash());

                        System.out.println("Bloque añadido. Hash: " + nuevoBloque.getHash());
                        out.println("OK:Registro guardado en Blockchain");

                        System.out.println("Sincronización completa: SQL (ID " + idGenerado + ") <-> Blockchain (Hash " + nuevoBloque.getHash().substring(0, 8) + "...)");
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
            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) {
                System.err.println("¡ALERTA! El hash del bloque " + i + " no coincide con sus datos.");
                return false;
            }

            // 2. Validar que el bloque actual apunte al hash del bloque anterior
            if (!currentBlock.getPreviousHash().equals(previousBlock.getHash())) {
                System.err.println("¡ALERTA! El bloque " + i + " no está bien enlazado con el bloque " + (i - 1));
                return false;
            }
        }
        return true;
    }
}