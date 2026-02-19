import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Scanner;

public class Cliente {

    static String remoteIP = "localhost";

    private static final String SENSOR_ID = "SENSOR_VALLE_01";

    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.trustStore", "miAlmacen.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "password");

        Scanner sc = new Scanner(System.in);
        SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (
                SSLSocket socket = (SSLSocket) ssf.createSocket(remoteIP, 6000);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            while (true) {
                System.out.print("Ingrese temperatura actual: ");
                double temp = sc.nextDouble();
                out.println(SENSOR_ID + ";" + "TEMP:" + temp);


                String respuesta = in.readLine();
                if (respuesta.equals("SISTEMA_APAGADO")) {
                    System.out.println("El servidor ha ordenado el apagado del equipo");
                    break;
                } else {
                    System.out.println("RESPUESTA:" + respuesta);
                }
            }
        } catch (IOException e) {
            System.err.println("Error de conexión");
        }
    }
}