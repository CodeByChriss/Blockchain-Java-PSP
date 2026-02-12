import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {

    static String remoteIP = "localhost";

    private static final String SENSOR_ID = "SENSOR_VALLE_01";

    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        try (
                Socket socket = new Socket(remoteIP, 6000);
                PrintWriter out = new PrintWriter(socket.getOutputStream(),true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ){

            System.out.println("Ingrese temperatura actual:");
            double temp = sc.nextDouble();
            out.println(SENSOR_ID + ";" +"TEMP:"+temp);


            String respuesta = in.readLine();
            if (respuesta.equals("SISTEMA_APAGADO")) {
                System.out.println("El servidor ha ordenado el apagado del equipo");
            }else{
                System.out.println("RESPUESTA:"+respuesta);
            }

        } catch (IOException e) {
            System.err.println("Error de conexión");
        }
    }
}