import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

// una instancia de esta clase representa un cliente con una conexión activa con el servidor
public class HiloServidor extends Thread {
    private SSLSocket clientSocket;
    private String sensor_ID;
    private volatile boolean continuar = true;
    private BufferedReader in;
    private PrintWriter out;
    private double TEMP_LIMITE;

    HiloServidor(SSLSocket clientSocket, double TEMP_LIMITE) throws IOException {
        this.clientSocket = clientSocket;
        this.sensor_ID = "";
        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        this.TEMP_LIMITE = TEMP_LIMITE;
    }

    public void run() {
        try {
            while (continuar) {
                String data = in.readLine();

                // Si el cliente cierra la conexión ordenadamente, data es null
                if (data == null) {
                    System.out.println("El cliente ha cerrado la conexión.");
                    break;
                }

                System.out.println(data);
                String sensorId = data.split(";")[0];
                String dataTemp = data.split(";")[1];
                double temp = Double.parseDouble(dataTemp.split(":")[1]);

                // si no hay sensor_id, lo guardamos
                if(this.sensor_ID.isEmpty()) this.sensor_ID = sensorId;

                if (!Servidor.auditarSistemaPorMayoria()) {
                    System.err.println("ERROR CRÍTICO: La base de datos Blockchain ha sido manipulada.");
                    out.println("ERROR:Integridad de red comprometida.");
                    Servidor.cerrarPorSeguridad(this,true,temp);
                    break; // Detener el servidor por seguridad
                }

                // 1. Guardar en la base de datos SQL remota (IP: 192.168.20.118)
                long timestamp = System.currentTimeMillis();
                int idGenerado = DatabaseService.guardarLectura(sensorId, temp, timestamp);

                if (idGenerado != -1) {
                    // 2. Crear el bloque en la Blockchain
                    // Usamos el hash previo de nuestra lista en memoria
                    String prevHash = Servidor.getLastHash();

                    // El 'data' del bloque será el hash de integridad de la BD SQL
                    Block nuevoBloque = new Block(sensorId, timestamp, temp, prevHash);
                    Servidor.addBlock(nuevoBloque);

                    // 3. Volver a la SQL para guardar la referencia del bloque (el sello final)
                    DatabaseService.vincularConBlockchain(idGenerado, nuevoBloque.getHash());

                    System.out.println("Bloque añadido. Hash: " + nuevoBloque.getHash());
                    out.println("OK:Registro guardado en Blockchain");

                    System.out.println("Sincronización completa: SQL (ID " + idGenerado + ") <-> Blockchain (Hash " + nuevoBloque.getHash().substring(0, 8) + "...)");
                }

                if (temp > this.TEMP_LIMITE) {
                    System.err.println("CRÍTICO: Temperatura " + temp + "°C excede el límite.");
                    System.out.println("Simulando apagado de seguridad del servidor...");
                    Servidor.cerrarPorSeguridad(this, false, temp);
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            // throw new RuntimeException(e);
        }
        Servidor.desconectarCliente(this);
    }

    public String getSensor_ID() {
        return this.sensor_ID;
    }

    public SSLSocket getClientSocket(){
        return this.clientSocket;
    }

    public void pararRun() {
        this.continuar = false;
        // Cerramos también el BufferedReader y PrintWriter para que no se quede el hilo colgado esperando una respuesta y se cierre
        try {
            this.in.close();
        }catch(IOException e){
            System.out.println("Error al cerrar el BufferedReader en el hiloServidor el cliente: "+this.sensor_ID);
        }
        this.out.close();
    }

    public void enviarMensaje(String msg){
        out.println(msg);
    }
}