package edu.cs;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Scanner;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@WebServlet("/FileUploadServlet")
@MultipartConfig(fileSizeThreshold=1024*1024*10,   // 10 MB
        maxFileSize=1024*1024*50,           // 50 MB
        maxRequestSize=1024*1024*100)       // 100 MB
public class FileUploadServlet extends HttpServlet {

    private static final long serialVersionUID = 205242440643911308L;

    private static final String UPLOAD_DIR = "uploads";

    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws ServletException, IOException {

        String applicationPath = request.getServletContext().getRealPath("");
        String uploadFilePath = applicationPath + File.separator + UPLOAD_DIR;

        File fileSaveDir = new File(uploadFilePath);
        if (!fileSaveDir.exists()) {
            fileSaveDir.mkdirs();
        }
        System.out.println("Upload File Directory=" + fileSaveDir.getAbsolutePath());
        System.out.println("Upload File Directory=" + fileSaveDir.getAbsolutePath());

        String fileName = "";
        try {
            for (Part part : request.getParts()) {
                fileName = getFileName(part);

                if (fileName != null && !fileName.isEmpty()) {
                    fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);
                    part.write(uploadFilePath + File.separator + fileName); // Save to the 'uploads' directory.
                }
            }
        } catch (IllegalStateException e) {
            System.out.println("File rejected due to size exceeding 50mb.");
            response.setContentType("text/html");
            response.getWriter().write("<h2>Upload Rejected</h2><p>File exceeds the 50 MB limit.</p>");
            return;
        }
        response.setContentType("text/html");
        String message = "Result";

        if (fileName == null || fileName.isEmpty()) {
            response.getWriter().write(message + "<BR>No file uploaded.");
            return;
        }

        String content = new Scanner(new File(uploadFilePath + File.separator + fileName))
                .useDelimiter("\\Z").next();


        /****** Integrate remote DB connection with this servlet, uncomment and modify the code below *******
         //ADD YOUR CODE HERE!
         ********/
        System.out.println("Starting DB section");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("Driver loaded");

        } catch (ClassNotFoundException e) {
            System.out.println("Driver load failed");
            throw new RuntimeException(e);
        }
        /* Using SSH, port 3307 on this machine is forwarded through
        an SSH tunnel to the MySQL database on the EC2 instance. */
        String jdbc_Url = "jdbc:mysql://18.117.150.218:3306/mydatabase";
        String db_User = "student";
        String db_Pass = "student123";

        Connection conn;
        try {
            System.out.println("About to connect to DB");
            conn = DriverManager.getConnection(jdbc_Url, db_User, db_Pass);
            System.out.println("Connected to DB");
        } catch (SQLException e) {
            System.out.println("DB connection failed");
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // First insert the file name and the file content.
        String insertSql = "INSERT INTO uploaded_files(file_name, file_content) VALUES (?, ?)";
        try {
            System.out.println("Preparing insert");
            PreparedStatement pstmt = conn.prepareStatement(insertSql);
            pstmt.setString(1, fileName);
            pstmt.setString(2, content);

            System.out.println("About to execute insert");
            pstmt.executeUpdate();
            System.out.println("Insert complete");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        /* Up to this point, the file name as well as it's contents
        * should be saved into our database. Now we have to prepare
        * SELECT queries to retrieve the file contents and send it back
        * to Tomcat.  */

        // SELECT data from mydatabase
        String selectSQL = "SELECT file_content FROM uploaded_files WHERE file_name = ? ORDER BY id DESC LIMIT 1";        String returned_content;
        try {
            System.out.println("Preparing select");
            PreparedStatement pstmt = conn.prepareStatement(selectSQL);

            System.out.println("Binding filename for select: " + fileName);
            pstmt.setString(1, fileName);

            System.out.println("About to execute select");
            ResultSet returned_query = pstmt.executeQuery();
            System.out.println("Select executed");

            if(returned_query.next()){
                System.out.println("Row found");
                returned_content = returned_query.getString("file_content");
                System.out.println("About to write response");
                response.getWriter().write(message + "<BR>" + returned_content);
                System.out.println("Retrieved file_content from DB");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }


    private String getFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        System.out.println("content-disposition header= " + contentDisp);
        String[] tokens = contentDisp.split(";");
        for (String token : tokens) {
            if (token.trim().startsWith("filename")) {
                return token.substring(token.indexOf("=") + 2, token.length() - 1);
            }
        }
        return "";
    }

    /* ----- Unused ----- */
    private void writeToResponse(HttpServletResponse resp, String results) throws IOException {
        PrintWriter writer = new PrintWriter(resp.getOutputStream());
        resp.setContentType("text/plain");

        if (results.isEmpty()) {
            writer.write("No results found.");
        } else {
            writer.write(results);
        }
        writer.close();
    }
}