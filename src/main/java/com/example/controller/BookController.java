package com.example.controller;

import com.example.model.Book;
import com.example.model.Category;
import com.example.service.BookService;
import com.example.dao.CategoryDAO;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookController {

    @FXML private TextField    searchField;
    @FXML private ComboBox<Category> categoryFilter;
    @FXML private TableView<Book> bookTable;
    @FXML private TableColumn<Book, Void>   colSTT;
    @FXML private TableColumn<Book, String> colIsbn;
    @FXML private TableColumn<Book, String> colTitle;
    @FXML private TableColumn<Book, String> colAuthor;
    @FXML private TableColumn<Book, String> colCategory;
    @FXML private TableColumn<Book, String> colTotal;
    @FXML private TableColumn<Book, String> colAvailable;
    @FXML private TableColumn<Book, Void>   colActions;
    @FXML private Label  statusLabel;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;
    @FXML private Label  pageInfoLabel;

    private static final int PAGE_SIZE = 20;
    private int          currentPage  = 0;
    private List<Book>   masterList   = new ArrayList<>();
    private List<Book>   filteredList = new ArrayList<>();

    private final BookService     bookService     = new BookService();
    private final CategoryDAO     categoryDAO     = new CategoryDAO();
    private List<Category>        categories;

    @FXML
    public void initialize() {
        setupColumns();
        loadCategories();
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        categoryFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        loadBooks();
    }

    private void setupColumns() {
        colSTT.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(currentPage * PAGE_SIZE + getIndex() + 1));
            }
        });
        colIsbn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIsbn()));
        colTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitle()));
        colAuthor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAuthor()));
        colCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategoryName()));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTotalCopies())));
        colAvailable.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getAvailableCopies())));
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("Sửa");
            private final Button deleteBtn = new Button("Xóa");
            private final HBox   box       = new HBox(6, editBtn, deleteBtn);
            {
                editBtn.getStyleClass().add("btn-outline");
                editBtn.setStyle("-fx-font-size:11px; -fx-padding:4 10 4 10;");
                deleteBtn.getStyleClass().add("btn-outline");
                deleteBtn.setStyle("-fx-font-size:11px; -fx-padding:4 10 4 10;");
                editBtn.setOnAction(e -> handleEdit(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void loadCategories() {
        categories = categoryDAO.findAll();
        categoryFilter.getItems().clear();
        categoryFilter.getItems().add(null);
        categoryFilter.getItems().addAll(categories);
    }

    private void applyFilter() {
        String kw       = searchField.getText().toLowerCase().trim();
        Category cat    = categoryFilter.getValue();
        filteredList = masterList.stream()
            .filter(b -> (cat == null
                          || (b.getCategoryId() != null && b.getCategoryId().intValue() == cat.getId()))
                      && (kw.isEmpty()
                          || b.getTitle().toLowerCase().contains(kw)
                          || b.getAuthor().toLowerCase().contains(kw)
                          || b.getIsbn().toLowerCase().contains(kw)))
            .sorted(java.util.Comparator.comparing(Book::getTitle))
            .toList();
        currentPage = 0;
        showPage();
    }

    private void showPage() {
        int from = currentPage * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, filteredList.size());
        bookTable.setItems(FXCollections.observableArrayList(
            from < filteredList.size() ? filteredList.subList(from, to) : List.of()));
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredList.size() / PAGE_SIZE));
        pageInfoLabel.setText("Trang " + (currentPage + 1) + " / " + totalPages);
        statusLabel.setText(filteredList.size() + " sách");
        prevPageBtn.setDisable(currentPage == 0);
        nextPageBtn.setDisable((currentPage + 1) * PAGE_SIZE >= filteredList.size());
        bookTable.refresh();
    }

    @FXML private void prevPage() { if (currentPage > 0) { currentPage--; showPage(); } }
    @FXML private void nextPage() { if ((currentPage + 1) * PAGE_SIZE < filteredList.size()) { currentPage++; showPage(); } }

    @FXML private void handleSearch() { applyFilter(); }

    private void loadBooks() {
        masterList = bookService.getAllBooks();
        applyFilter();
    }

    @FXML
    private void handleAdd() {
        showBookDialog(null);
    }

    private void handleEdit(Book book) {
        showBookDialog(book);
    }

    private void handleDelete(Book book) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Xóa sách \"" + book.getTitle() + "\" khỏi danh sách?",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Xác nhận xóa sách");
        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.YES) {
                try {
                    bookService.deleteBook(book.getId());
                    loadBooks();
                    showInfo("Đã xóa sách khỏi danh sách.");
                } catch (Exception e) {
                    showError(e.getMessage());
                }
            }
        });
    }

    private void showBookDialog(Book book) {
        boolean isEdit = (book != null);
        Dialog<Book> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Cập nhật sách" : "Thêm sách mới");
        dialog.setHeaderText(null);

        ButtonType saveType = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField isbnField     = new TextField(isEdit ? book.getIsbn()      : "");
        TextField titleField    = new TextField(isEdit ? book.getTitle()     : "");
        TextField authorField   = new TextField(isEdit ? book.getAuthor()    : "");
        TextField publisherField= new TextField(isEdit ? book.getPublisher() : "");
        TextField yearField     = new TextField(isEdit && book.getPublishYear() != null ? String.valueOf(book.getPublishYear()) : "");
        TextField copiesField   = new TextField(isEdit ? String.valueOf(book.getTotalCopies()) : "1");
        TextArea  descArea      = new TextArea(isEdit ? book.getDescription() : "");
        ComboBox<Category> catCombo = new ComboBox<>();
        catCombo.getItems().addAll(categories);
        if (isEdit && book.getCategoryId() != null) catCombo.getItems().stream()
            .filter(c -> c.getId() == book.getCategoryId().intValue()).findFirst()
            .ifPresent(catCombo::setValue);

        titleField.setPrefWidth(320);
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        grid.add(new Label("ISBN:"),       0, 0); grid.add(isbnField,      1, 0);
        grid.add(new Label("Tên sách *:"), 0, 1); grid.add(titleField,     1, 1);
        grid.add(new Label("Tác giả *:"),  0, 2); grid.add(authorField,    1, 2);
        grid.add(new Label("NXB:"),        0, 3); grid.add(publisherField,  1, 3);
        grid.add(new Label("Năm XB:"),     0, 4); grid.add(yearField,      1, 4);
        grid.add(new Label("Danh mục:"),   0, 5); grid.add(catCombo,       1, 5);
        grid.add(new Label("Số lượng *:"), 0, 6); grid.add(copiesField,    1, 6);
        grid.add(new Label("Mô tả:"),      0, 7); grid.add(descArea,       1, 7);

        Label formError = new Label();
        formError.setVisible(false);
        formError.setManaged(false);
        formError.setWrapText(true);
        formError.setMaxWidth(360);
        formError.setStyle("-fx-text-fill: #E11D48; -fx-font-size: 12px;");
        grid.add(formError, 0, 8, 2, 1);

        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            List<String> errs = new ArrayList<>();
            if (titleField.getText().isBlank()) {
                errs.add("• Tên sách không được để trống.");
                titleField.setStyle("-fx-border-color: #E11D48;");
            }
            if (authorField.getText().isBlank()) {
                errs.add("• Tác giả không được để trống.");
                authorField.setStyle("-fx-border-color: #E11D48;");
            }
            try {
                int c = Integer.parseInt(copiesField.getText().trim());
                if (c < 1) {
                    errs.add("• Số lượng phải lớn hơn 0.");
                    copiesField.setStyle("-fx-border-color: #E11D48;");
                }
            } catch (NumberFormatException ex) {
                errs.add("• Số lượng phải là số nguyên dương.");
                copiesField.setStyle("-fx-border-color: #E11D48;");
            }
            if (!errs.isEmpty()) {
                formError.setText(String.join("\n", errs));
                formError.setVisible(true);
                formError.setManaged(true);
                evt.consume();
            }
        });
        titleField.textProperty().addListener((obs, o, n) -> { titleField.setStyle(""); formError.setVisible(false); formError.setManaged(false); });
        authorField.textProperty().addListener((obs, o, n) -> { authorField.setStyle(""); formError.setVisible(false); formError.setManaged(false); });
        copiesField.textProperty().addListener((obs, o, n) -> { copiesField.setStyle(""); formError.setVisible(false); formError.setManaged(false); });

        dialog.setResultConverter(btnType -> {
            if (btnType == saveType) {
                Book b = isEdit ? book : new Book();
                b.setIsbn(isbnField.getText().trim());
                b.setTitle(titleField.getText().trim());
                b.setAuthor(authorField.getText().trim());
                b.setPublisher(publisherField.getText().trim());
                try { b.setPublishYear(yearField.getText().isBlank() ? null : Integer.parseInt(yearField.getText().trim())); }
                catch (NumberFormatException ignored) {}
                b.setCategoryId(catCombo.getValue() != null ? catCombo.getValue().getId() : null);
                try { b.setTotalCopies(Integer.parseInt(copiesField.getText().trim())); }
                catch (NumberFormatException ignored) { b.setTotalCopies(1); }
                b.setDescription(descArea.getText().trim());
                return b;
            }
            return null;
        });

        Optional<Book> result = dialog.showAndWait();
        result.ifPresent(b -> {
            try {
                if (isEdit) bookService.updateBook(b);
                else        bookService.addBook(b);
                loadBooks();
                loadCategories();
                showInfo(isEdit ? "Cập nhật sách thành công." : "Thêm sách thành công.");
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Lỗi"); a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }
}
