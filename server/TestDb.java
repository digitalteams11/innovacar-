import java.sql.Connection;
import java.sql.DriverManager;
public class TestDb {
    public static void main(String[] args) {
        String[] passwords = {"", "postgres", "root", "admin", "password", "123456"};
        for (String p : passwords) {
            try {
                Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", p);
                System.out.println("SUCCESS WITH PASSWORD: " + p);
                c.close();
                return;
            } catch (Exception e) {}
        }
        System.out.println("ALL FAILED");
    }
}
