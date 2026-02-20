import javax.net.ssl.*;
import java.io.*;
import java.net.SocketException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

public class Servidor {

    private static List<Block> blockchain = new ArrayList<Block>();
    private static final double TEMP_LIMITE = 50.0;
    private static ArrayList<HiloServidor> hilosServidor = new ArrayList<HiloServidor>();
    private static volatile boolean continuar = true;
    private static SSLServerSocket serverSocket;

    public static void main(String[] args) {
        try {
            // Cargamos el certificado
            char[] password = "password".toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new FileInputStream("miAlmacen.jks"), password);

            // Inicializamos el KeyManager
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Socket seguro
            SSLServerSocketFactory sslServerSocket = sslContext.getServerSocketFactory();
            serverSocket = (SSLServerSocket) sslServerSocket.createServerSocket(6000); // lo saco de try() para poner forzar el cierre del servidor
            System.out.println("Servidor SSL de Monitoreo listo en puerto 6000...");

            while (continuar) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                // Creamos un nuevo hilo para cada cliente nuevo
                HiloServidor hiloServidor = new HiloServidor(clientSocket, TEMP_LIMITE);
                hiloServidor.start();
                hilosServidor.add(hiloServidor);
            }
        } catch (IOException | KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException |
                 KeyStoreException | CertificateException e) {
            if (!continuar) {
                System.out.println("Servidor cerrado de forma controlada.");
            } else {
                e.printStackTrace();
            }
        }
    }

    public static boolean auditarSistemaPorMayoria() {
        List<Block> datosSQL = DatabaseService.obtenerTodaBD();
        if (datosSQL.isEmpty()) return true;

        int votosValidos = 0;
        int votosCorruptos = 0;
        String hashAnterior = "";

        for (int i = 0; i < datosSQL.size(); i++) {
            Block bloqueSQL = datosSQL.get(i);
            // Creamos un bloque auxiliar pasandole los datos actuales para que calcule el hash actual
            Block bloqueAuxiliar = new Block(bloqueSQL.getId(), bloqueSQL.getTimestamp(), bloqueSQL.getTemp(), hashAnterior);
            hashAnterior = bloqueSQL.getHash();

            // comprobamos si ambos hashes son iguales
            boolean integridadSQL = bloqueSQL.getHash().equals(bloqueAuxiliar.getHash());

            if (integridadSQL) {
                votosValidos++;
            } else {
                votosCorruptos++;
                System.err.println("Divergencia detectada en el registro con índice: " + i);
                System.err.println(bloqueSQL.getHash() + " <> " + bloqueAuxiliar.getHash());
            }
        }

        System.out.println("RESULTADO AUDITORÍA: Válidos[" + votosValidos + "] Corruptos[" + votosCorruptos + "]");

        if (votosCorruptos > votosValidos || (votosCorruptos == votosValidos && votosCorruptos > 0)) {
            System.err.println("CRÍTICO: La mayoría de la base de datos o la cadena están corruptas (" + votosCorruptos + " registros corruptos). Cierre de seguridad.");
            return false; // Se cancela la operación
        } else if (votosCorruptos > 0) {
            System.out.println("ADVERTENCIA: Se han detectado registros corruptos, pero la mayoría es íntegra (" + votosCorruptos + " registros corruptos). Continuando...");
        }

        return true;
    }

    public synchronized static String getLastHash() {
        if (blockchain.isEmpty()) return "";
        else return blockchain.getLast().getHash();
    }

    public synchronized static void addBlock(Block nuevoBlock) {
        blockchain.add(nuevoBlock);
    }

    public synchronized static void cerrarPorSeguridad(HiloServidor hiloServidor, boolean divergencia, double temp) {
        if (divergencia) {
            System.err.println("CRÍTICO: Divergencia detectada.");
        } else {
            System.err.println("CRÍTICO: Temperatura " + temp + "°C excede el límite.");
        }
        System.out.println("Simulando apagado de seguridad del servidor...");

        // enviamos a todos los clientes que el servidor se ha apagado
        for (HiloServidor hilo : hilosServidor) {
            hilo.enviarMensaje("SISTEMA_APAGADO");
            hilo.pararRun();
        }

        continuar = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.out.println("Error al intentar cerrar el socket del servidor.");
        }
    }

    public synchronized static void desconectarCliente(HiloServidor hiloServidor) {
        hilosServidor.remove(hiloServidor);
        try {
            hiloServidor.getClientSocket().close();
        } catch (IOException e) {
            System.out.println("Error al intentar cerrar el socket del cliente.");
        }
        System.out.println("Cliente " + hiloServidor.getSensor_ID() + " desconectado.");
    }
}