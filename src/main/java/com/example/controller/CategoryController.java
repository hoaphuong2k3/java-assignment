package com.example.controller;

import com.example.dao.CategoryDAO;
import com.example.model.Book;
import com.example.model.Category;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryController {

    private enum CategoryListSort {
        AZ_BY_NAME,
        NEWEST_FIRST
    }

    @FXML private TextField  searchField;
    @FXML private TableView<Category> categoryTable;
    @FXML private TableColumn<Category, Void>   colSelect;
    @FXML private TableColumn<Category, Void>   colSTT;
    @FXML private TableColumn<Category, String> colName;
    @FXML private TableColumn<Category, String> colBooks;
    @FXML private TableColumn<Category, String> colDesc;
    @FXML private TableColumn<Category, Void>   colCatActions;
    @FXML private Label      statusLabel;
    @FXML private Button     prevPageBtn;
    @FXML private Button     nextPageBtn;
    @FXML private Label      pageInfoLabel;

    @FXML private Label      formTitle;
    @FXML private TextField  nameField;
    @FXML private TextArea   descField;
    @FXML private Button     saveButton;
    @FXML private Button     deleteButton;
    @FXML private Label      formMessage;

    private static final int PAGE_SIZE = 20;
    private int currentPage = 0;
    private List<Category> masterList   = new ArrayList<>();
    private List<Category> filteredList = new ArrayList<>();
    private CategoryListSort categoryListSort = CategoryListSort.AZ_BY_NAME;

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private Category selectedCategory;
    private final Map<Integer, BooleanProperty> selectedMap = new HashMap<>();

    @FXML
    public void initialize() {
        setupColumns();
        searchField.textProperty().addListener((obs, o, n) -> {
            categoryListSort = CategoryListSort.AZ_BY_NAME;
            applyFilter(true);
        });
        loadCategories();
    }

    private void setupColumns() {
        colSelect.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            {
                cb.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        Category c = getTableView().getItems().get(idx);
                        selectedMap.computeIfAbsent(c.getId(), k -> new SimpleBooleanProperty(false))
                                   .set(cb.isSelected());
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Category c = getTableView().getItems().get(getIndex());
                BooleanProperty prop = selectedMap.computeIfAbsent(
                    c.getId(), k -> new SimpleBooleanProperty(false));
                cb.setSelected(prop.get());
                setGraphic(cb);
            }
        });

        colSTT.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(currentPage * PAGE_SIZE + getIndex() + 1));
            }
        });

        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colBooks.setCellValueFactory(c -> new SimpleStringProperty(
            String.valueOf(categoryDAO.countBooksInCategory(c.getValue().getId()))));
        colDesc.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getDescription() != null ? c.getValue().getDescription() : ""));

        colCatActions.setCellFactory(col -> new TableCell<>() {
            private final Button eyeBtn = new Button();
            {
                FontIcon icon = new FontIcon("fas-eye");
                icon.setIconSize(14);
                eyeBtn.setGraphic(icon);
                eyeBtn.setTooltip(new Tooltip("Xem sách trong danh mục"));
                eyeBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
                eyeBtn.getStyleClass().add("btn-outline");
                eyeBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size())
                        handleViewBooks(getTableView().getItems().get(idx));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : eyeBtn);
            }
        });
    }

    private void rebuildFiltered() {
        String kw = searchField.getText().trim().toLowerCase();
        Comparator<Category> order = categoryListSort == CategoryListSort.NEWEST_FIRST
            ? Comparator.comparing(Category::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                .thenComparing(Category::getName, String.CASE_INSENSITIVE_ORDER)
            : Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER);
        filteredList = masterList.stream()
            .filter(c -> kw.isEmpty() || c.getName().toLowerCase().contains(kw))
            .sorted(order)
            .toList();
    }

    /** @param resetPage đặt true khi đổi ô tìm kiếm; false khi reload để giữ trang hiện tại. */
    private void applyFilter(boolean resetPage) {
        rebuildFiltered();
        if (resetPage) currentPage = 0;
        else clampCurrentPage();
        showPage();
    }

    private void clampCurrentPage() {
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredList.size() / PAGE_SIZE));
        if (currentPage >= totalPages) currentPage = Math.max(0, totalPages - 1);
    }

    private void showPage() {
        int from = currentPage * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, filteredList.size());
        categoryTable.setItems(FXCollections.observableArrayList(
            from < filteredList.size() ? filteredList.subList(from, to) : List.of()));
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredList.size() / PAGE_SIZE));
        pageInfoLabel.setText("Trang " + (currentPage + 1) + " / " + totalPages);
        statusLabel.setText(filteredList.size() + " danh mục");
        prevPageBtn.setDisable(currentPage == 0);
        nextPageBtn.setDisable((currentPage + 1) * PAGE_SIZE >= filteredList.size());
        categoryTable.refresh();
    }

    @FXML private void prevPage() { if (currentPage > 0) { currentPage--; showPage(); } }
    @FXML private void nextPage() { if ((currentPage + 1) * PAGE_SIZE < filteredList.size()) { currentPage++; showPage(); } }

    @FXML
    private void handleSearch() {
        categoryListSort = CategoryListSort.AZ_BY_NAME;
        applyFilter(true);
    }

    @FXML
    private void handleTableClick() {
        Category c = categoryTable.getSelectionModel().getSelectedItem();
        if (c == null) return;
        selectedCategory = c;
        nameField.setText(c.getName());
        descField.setText(c.getDescription() != null ? c.getDescription() : "");
        formTitle.setText("Cập nhật danh mục");
        saveButton.setText("Cập nhật");
        deleteButton.setVisible(true);
        deleteButton.setManaged(true);
        formMessage.setText("");
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            setMessage("Tên danh mục không được để trống.", true);
            return;
        }
        Integer excludeId = selectedCategory == null ? null : selectedCategory.getId();
        if (categoryDAO.existsNameForOtherCategory(name, excludeId)) {
            setMessage("Tên danh mục đã tồn tại (không phân biệt chữ hoa/thường).", true);
            return;
        }
        try {
            if (selectedCategory == null) {
                Category c = new Category(name, descField.getText().trim());
                categoryDAO.save(c);
                setMessage("Thêm danh mục thành công.", false);
                handleClear();
                loadCategoriesAfterAdd();
            } else {
                selectedCategory.setName(name);
                selectedCategory.setDescription(descField.getText().trim());
                categoryDAO.update(selectedCategory);
                setMessage("Cập nhật thành công.", false);
                handleClear();
                loadCategories();
            }
        } catch (Exception e) {
            setMessage("Lỗi: " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedCategory == null) return;
        int active = categoryDAO.countBorrowingOrOverdueInCategory(selectedCategory.getId());
        if (active > 0) {
            setMessage("Không thể xóa danh mục: còn " + active
                + " phiếu mượn đang mượn hoặc quá hạn chưa trả (sách trong danh mục).", true);
            return;
        }
        long bookCount = categoryDAO.countBooksInCategory(selectedCategory.getId());
        if (bookCount > 0) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Xóa danh mục");
            alert.setHeaderText("Danh mục này đang có " + bookCount + " sách.");
            Label msg = new Label("Bạn có muốn xóa tất cả dữ liệu trong danh mục này không?");
            msg.setWrapText(true);
            CheckBox confirmBooks = new CheckBox("Delete All — xóa luôn " + bookCount + " sách trong danh mục");
            confirmBooks.setWrapText(true);
            Label note = new Label("Sách sẽ được đánh dấu ẩn (soft delete).");
            note.setStyle("-fx-font-size:11px; -fx-text-fill:#64748B;");
            VBox content = new VBox(8, msg, confirmBooks, note);
            content.setPadding(new Insets(8));
            alert.getDialogPane().setContent(content);
            ButtonType proceed = new ButtonType("Xóa danh mục", ButtonBar.ButtonData.YES);
            ButtonType cancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(proceed, cancel);
            alert.showAndWait().ifPresent(t -> {
                if (t == proceed) {
                    if (!confirmBooks.isSelected()) {
                        setMessage("Vui lòng tick xác nhận xóa toàn bộ sách trong danh mục.", true);
                        return;
                    }
                    categoryDAO.deleteWithBooks(selectedCategory.getId());
                    setMessage("Đã xóa danh mục và " + bookCount + " sách.", false);
                    handleClear();
                    loadCategories();
                }
            });
        } else {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xóa danh mục \"" + selectedCategory.getName() + "\"?",
                ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(t -> {
                if (t == ButtonType.YES) {
                    categoryDAO.delete(selectedCategory.getId());
                    setMessage("Đã xóa danh mục.", false);
                    handleClear();
                    loadCategories();
                }
            });
        }
    }

    @FXML
    private void handleBulkDelete() {
        List<Integer> toDelete = selectedMap.entrySet().stream()
            .filter(e -> e.getValue().get())
            .map(Map.Entry::getKey)
            .toList();
        if (toDelete.isEmpty()) {
            setMessage("Chưa chọn danh mục nào để xóa.", true);
            return;
        }
        CheckBox deleteBooksToo = new CheckBox("Cho phép xóa luôn sách trong danh mục có sách (soft delete)");
        deleteBooksToo.setWrapText(true);
        VBox box = new VBox(8,
            new Label("Xóa " + toDelete.size() + " danh mục đã chọn?"), deleteBooksToo);
        box.setPadding(new Insets(8));
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xóa hàng loạt");
        confirm.setHeaderText(null);
        confirm.getDialogPane().setContent(box);
        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(t -> {
            if (t != ButtonType.YES) return;
            int deleted = 0;
            int skipped = 0;
            int blockedLoans = 0;
            for (int id : toDelete) {
                if (categoryDAO.countBorrowingOrOverdueInCategory(id) > 0) {
                    blockedLoans++;
                    continue;
                }
                long n = categoryDAO.countBooksInCategory(id);
                if (n == 0) {
                    categoryDAO.delete(id);
                    deleted++;
                } else if (deleteBooksToo.isSelected()) {
                    categoryDAO.deleteWithBooks(id);
                    deleted++;
                } else {
                    skipped++;
                }
            }
            selectedMap.clear();
            loadCategories();
            String msg = "Đã xử lý " + deleted + " danh mục.";
            if (skipped > 0) msg += " Bỏ qua " + skipped + " danh mục có sách (chưa tick xóa sách).";
            if (blockedLoans > 0) msg += " Không xóa " + blockedLoans + " danh mục còn phiếu đang mượn/quá hạn.";
            setMessage(msg, skipped > 0 || blockedLoans > 0);
        });
    }

    private void handleViewBooks(Category category) {
        List<Book> books = categoryDAO.findBooksByCategory(category.getId());
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Sách trong danh mục: " + category.getName());
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (books.isEmpty()) {
            dialog.getDialogPane().setContent(new Label("Danh mục này chưa có sách nào."));
        } else {
            TableView<Book> bt = new TableView<>();
            TableColumn<Book, String> tcIsbn   = new TableColumn<>("ISBN");
            TableColumn<Book, String> tcTitle  = new TableColumn<>("Tên sách");
            TableColumn<Book, String> tcAuthor = new TableColumn<>("Tác giả");
            TableColumn<Book, String> tcTotal  = new TableColumn<>("Tổng SL");
            TableColumn<Book, String> tcAvail  = new TableColumn<>("Còn lại");
            tcIsbn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIsbn()));
            tcTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitle()));
            tcAuthor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAuthor()));
            tcTotal.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTotalCopies())));
            tcAvail.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getAvailableCopies())));
            tcIsbn.setPrefWidth(110); tcTitle.setPrefWidth(240); tcAuthor.setPrefWidth(150);
            tcTotal.setPrefWidth(70); tcAvail.setPrefWidth(70);
            bt.getColumns().addAll(tcIsbn, tcTitle, tcAuthor, tcTotal, tcAvail);
            bt.setItems(FXCollections.observableArrayList(books));
            bt.setPrefWidth(660); bt.setPrefHeight(340);
            VBox content = new VBox(8, new Label("Tổng: " + books.size() + " sách"), bt);
            content.setPadding(new Insets(16));
            dialog.getDialogPane().setContent(content);
        }
        dialog.showAndWait();
    }

    @FXML
    private void handleClear() {
        selectedCategory = null;
        nameField.clear();
        descField.clear();
        formTitle.setText("Thêm danh mục mới");
        saveButton.setText("Lưu");
        deleteButton.setVisible(false);
        deleteButton.setManaged(false);
        formMessage.setText("");
        categoryTable.getSelectionModel().clearSelection();
    }

    private void loadCategories() {
        categoryListSort = CategoryListSort.AZ_BY_NAME;
        masterList = categoryDAO.findAll();
        applyFilter(false);
    }

    /** Sau thêm danh mục mới: mới nhất lên đầu, trang 1. */
    private void loadCategoriesAfterAdd() {
        categoryListSort = CategoryListSort.NEWEST_FIRST;
        masterList = categoryDAO.findAll();
        applyFilter(true);
    }

    private void setMessage(String msg, boolean isError) {
        formMessage.setText(msg);
        formMessage.setStyle(isError ? "-fx-text-fill: #DC2626;" : "-fx-text-fill: #16A34A;");
    }
}
