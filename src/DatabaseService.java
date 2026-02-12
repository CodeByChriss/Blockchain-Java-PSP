import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/*
    DESCRIBE DE LA BASE DE DATOS:

    +-----------------+--------------+------+-----+---------+----------------+
    | Field           | Type         | Null | Key | Default | Extra          |
    +-----------------+--------------+------+-----+---------+----------------+
    | id              | int          | NO   | PRI | NULL    | auto_increment |
    | sensor_id       | varchar(50)  | NO   |     | NULL    |                |
    | valor_temp      | decimal(5,2) | NO   |     | NULL    |                |
    | fecha_registro  | timestamp(3) | YES  |     | NULL    |                |
    | blockchain_hash | varchar(64)  | YES  |     | NULL    |                |
    +-----------------+--------------+------+-----+---------+----------------+

    La diferencia con el script proporcionado por el profesor es en el tipo de
    fecha_registro que le he puesto (3) para que no redondee los decimales.
 */

public class DatabaseService {
    // Configuración de la base de datos remota
    // private static final String IP = "192.168.72.1";
    private static final String IP = "localhost";
    private static final String DB = "sistema_monitoreo";
    private static final String URL = "jdbc:mysql://" + IP + ":3306/" + DB + "?useSSL=false&serverTimezone=UTC" + "&allowPublicKeyRetrieval=true";
    private static final String USER = "usuario_bd"; // Cambia por tu usuario
    private static final String PASS = "password"; // Cambia por tu contraseña


    static {
        try {
            // Forzamos la carga de la clase del Driver
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("¡Error! No se encontró el driver de MySQL en el Classpath.");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws Exception {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    /**
     * Inserta la lectura y devuelve el ID generado para poder hashearlo después.
     */
    public static int guardarLectura(String sensorId, double temp, long timestamp) {
        String sql = "INSERT INTO lecturas_temperatura (sensor_id, valor_temp, fecha_registro) VALUES (?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, sensorId);
            pstmt.setDouble(2, temp);
            pstmt.setTimestamp(3, new Timestamp(timestamp));
            pstmt.executeUpdate();

            // Recuperar el ID autoincremental
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            System.err.println("Error al guardar en BD: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Actualiza la fila con el hash del bloque de la Blockchain.
     */
    public static void vincularConBlockchain(int idRegistro, String blockHash) {
        String sql = "UPDATE lecturas_temperatura SET blockchain_hash = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, blockHash);
            pstmt.setInt(2, idRegistro);
            pstmt.executeUpdate();

        } catch (Exception e) {
            System.err.println("Error al vincular con Blockchain: " + e.getMessage());
        }
    }

    public static List<Block> obtenerTodaBD() {
        List<Block> registrosBD = new ArrayList<>();
        String sql = "SELECT id, sensor_id, valor_temp, fecha_registro, blockchain_hash FROM lecturas_temperatura ORDER BY id ASC";

        try (
                Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)
        ) {
            while (rs.next()) {
                // Reconstruimos el bloque con los datos que hay en la base de datos actualmente
                Block b = new Block(
                        rs.getString("sensor_id"),
                        rs.getTimestamp("fecha_registro").getTime(),
                        rs.getDouble("valor_temp"),
                        "", // El previousHash lo validamos en el servidor
                        rs.getString("blockchain_hash")
                );
                registrosBD.add(b);
            }
        } catch (Exception e) {
            System.err.println("Error al obtener toda la base de datos: " + e.getMessage());
        }
        return registrosBD;
    }
}