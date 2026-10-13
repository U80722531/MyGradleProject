package mygradleproject.controller;

import mygradleproject.model.Task;
import mygradleproject.model.User;
import mygradleproject.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.fxml.FXML;
import java.time.LocalDate;
import java.util.Set;

public class TaskDialogController {
    @FXML private Label headerLabel;
    @FXML private TableView<Task> taskTable;
    @FXML private TableColumn<Task, String> taskTitleColumn;
    @FXML private TableColumn<Task, LocalDate> taskDateColumn;
    @FXML private TableColumn<Task, Void> taskDeleteColumn;
    @FXML private TextField taskTitleField;
    @FXML private DatePicker taskDatePicker;
    @FXML private Label dialogErrorLabel;

    private User user;
    private UserRepository userRepository;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private Runnable onUpdateCallback;

    @FXML
    public void initialize() {
        taskTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        taskDateColumn.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        setupDeleteButtonColumn();
        taskDatePicker.setValue(LocalDate.now());
    }

    public void initData(User user, UserRepository userRepository, Runnable onUpdateCallback) {
        this.user = user;
        this.userRepository = userRepository;
        this.onUpdateCallback = onUpdateCallback;
        headerLabel.setText("Aufgaben für: " + user.getName());
        refreshTable();
    }

    private void refreshTable() {
        taskTable.setItems(FXCollections.observableArrayList(user.getTasks()));
    }

    @FXML
    private void handleAddTask() {
        dialogErrorLabel.setText("");
        String title = taskTitleField.getText();
        LocalDate date = taskDatePicker.getValue();

        Task task = new Task(title, date, user);
        Set<ConstraintViolation<Task>> violations = validator.validate(task);

        if (!violations.isEmpty()) {
            dialogErrorLabel.setText(violations.iterator().next().getMessage());
            return;
        }

        user.getTasks().add(task);
        userRepository.save(user);
        taskTitleField.clear();
        refreshTable();
        if (onUpdateCallback != null) onUpdateCallback.run();
    }

    private void setupDeleteButtonColumn() {
        taskDeleteColumn.setCellFactory(param -> new TableCell<>() {
            private final Button deleteButton = new Button("X");
            {
                deleteButton.setStyle("-fx-background-color: #ff4d4d; -fx-text-fill: white;");
                deleteButton.setOnAction(event -> {
                    Task task = getTableView().getItems().get(getIndex());
                    user.getTasks().remove(task);
                    userRepository.save(user);
                    refreshTable();
                    if (onUpdateCallback != null) onUpdateCallback.run();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });
    }

    @FXML
    private void handleClose() {
        ((Stage) taskTable.getScene().getWindow()).close();
    }
}
