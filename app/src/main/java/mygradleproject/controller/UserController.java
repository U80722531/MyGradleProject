package mygradleproject.controller;

import mygradleproject.config.EntityManagerUtil;
import mygradleproject.model.Task;
import mygradleproject.model.User;
import mygradleproject.repository.UserRepository;
import mygradleproject.repository.UserRepositoryImpl;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import java.io.*;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.*;

public class UserController {
    @FXML private TextField searchField;
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, Long> idColumn;
    @FXML private TableColumn<User, String> nameColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, Void> deleteColumn;
    
    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private Label errorLabel;
    @FXML private ProgressBar progressBar;
    
    @FXML private Label totalUsersLabel;
    @FXML private Label totalTasksLabel;
    @FXML private PieChart taskPieChart;
    @FXML private BarChart<String, Number> userBarChart;

    private UserRepository userRepository;
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @FXML
    public void initialize() {
        EntityManager em = EntityManagerUtil.getEntityManager();
        userRepository = new UserRepositoryImpl(em);

        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        
        setupDeleteColumn();
        setupRowDoubleClick();
        loadUserData();
        setupFiltering();
    }

    private void loadUserData() {
        userList.setAll(userRepository.findAll());
        updateStatistics();
    }

    private void setupFiltering() {
        FilteredList<User> filteredData = new FilteredList<>(userList, p -> true);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(user -> {
                if (newVal == null || newVal.isEmpty()) return true;
                String lower = newVal.toLowerCase();
                return user.getName().toLowerCase().contains(lower) || user.getEmail().toLowerCase().contains(lower);
            });
        });
        SortedList<User> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(userTable.comparatorProperty());
        userTable.setItems(sortedData);
    }

    @FXML
    private void handleSaveUser() {
        errorLabel.setText("");
        User user = new User(nameField.getText(), emailField.getText());
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        if (!violations.isEmpty()) {
            errorLabel.setText(violations.iterator().next().getMessage());
            return;
        }

        userRepository.save(user);
        nameField.clear();
        emailField.clear();
        loadUserData();
    }

    private void setupDeleteColumn() {
        deleteColumn.setCellFactory(param -> new TableCell<>() {
            private final Button btn = new Button("Löschen");
            {
                btn.setStyle("-fx-background-color: #ff4d4d; -fx-text-fill: white;");
                btn.setOnAction(e -> {
                    User user = getTableView().getItems().get(getIndex());
                    userRepository.deleteById(user.getId());
                    loadUserData();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    private void setupRowDoubleClick() {
        userTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && userTable.getSelectionModel().getSelectedItem() != null) {
                openTaskDialog(userTable.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void openTaskDialog(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/task_dialog.fxml"));
            Parent root = loader.load();
            TaskDialogController controller = loader.getController();
            controller.initData(user, userRepository, this::updateStatistics);

            Stage stage = new Stage();
            stage.setTitle("Aufgaben verwalten");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(userTable.getScene().getWindow());
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadUserData();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateStatistics() {
        long totalUsers = userList.size();
        long totalTasks = userList.stream().mapToLong(u -> u.getTasks().size()).sum();
        long overdue = userList.stream().flatMap(u -> u.getTasks().stream()).filter(t -> t.getDueDate().isBefore(LocalDate.now())).count();
        long open = totalTasks - overdue;

        totalUsersLabel.setText("Benutzer gesamt: " + totalUsers);
        totalTasksLabel.setText("Aufgaben gesamt: " + totalTasks);

        taskPieChart.getData().setAll(
            new PieChart.Data("Offen (" + open + ")", open),
            new PieChart.Data("Überfällig (" + overdue + ")", overdue)
        );

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Tasks");
        userList.stream()
            .sorted((u1, u2) -> Integer.compare(u2.getTasks().size(), u1.getTasks().size()))
            .limit(5)
            .forEach(u -> series.getData().add(new XYChart.Data<>(u.getName(), u.getTasks().size())));

        userBarChart.getData().clear();
        userBarChart.getData().add(series);
    }

    @FXML
    private void handleExportCSV() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(userTable.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            writer.println("ID;Name;Email;Tasks");
            for (User u : userList) {
                StringBuilder sb = new StringBuilder();
                for (Task t : u.getTasks()) {
                    sb.append(t.getTitle()).append(" (").append(t.getDueDate()).append(")|");
                }
                writer.println(u.getId() + ";" + u.getName() + ";" + u.getEmail() + ";" + sb.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleImportCSV() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showOpenDialog(userTable.getScene().getWindow());
        if (file == null) return;

        progressBar.setVisible(true);
        progressBar.setProgress(0);

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                    String line;
                    reader.readLine(); // Header skip
                    while ((line = reader.readLine()) != null) lines.add(line);
                }

                for (int i = 0; i < lines.size(); i++) {
                    String[] tokens = lines.get(i).split(";");
                    if (tokens.length >= 3) {
                        User u = new User(tokens[0], tokens[1]); // Minimal implementation validation safe
                        Platform.runLater(() -> {
                            userRepository.save(u);
                            loadUserData();
                        });
                    }
                    updateProgress(i + 1, lines.size());
                    Thread.sleep(100); // Artificial delay to make progress animation visible
                }
                return null;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            loadUserData();
        });
        new Thread(task).start();
    }

    @FXML
    private void handleExportPDF() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showSaveDialog(userTable.getScene().getWindow());
        if (file == null) return;

        Document document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();
            document.add(new Paragraph("System Report - Benutzer & Aufgaben"));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(3);
            table.addCell("ID");
            table.addCell("Name");
            table.addCell("E-Mail");

            for (User u : userList) {
                table.addCell(String.valueOf(u.getId()));
                table.addCell(u.getName());
                table.addCell(u.getEmail());
            }
            document.add(table);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            document.close();
        }
    }
}
