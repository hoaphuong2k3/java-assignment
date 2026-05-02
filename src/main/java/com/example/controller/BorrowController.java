package com.example.controller;

import com.example.dao.BookDAO;
import com.example.dao.FineDAO;
import com.example.dao.MemberDAO;
import com.example.model.Book;
import com.example.model.BorrowRecord;
import com.example.model.Fine;
import com.example.model.Member;
import com.example.service.BorrowService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class BorrowController {

    @FXML private TextField    searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private DatePicker   fromDatePicker;
    @FXML private DatePicker   toDatePicker;
    @FXML private TableView<BorrowRecord> borrowTable;
    @FXML private TableColumn<BorrowRecord, Void>   colSTT;
    @FXML private TableColumn<BorrowRecord, String> colMember;
    @FXML private TableColumn<BorrowRecord, String> colBook;
    @FXML private TableColumn<BorrowRecord, String> colBorrowDate;
    @FXML private TableColumn<BorrowRecord, String> colDueDate;
    @FXML private TableColumn<BorrowRecord, String> colReturnDate;
    @FXML private TableColumn<BorrowRecord, String> colStatus;
    @FXML private TableColumn<BorrowRecord, String> colNote;
    @FXML private TableColumn<BorrowRecord, Void>   colActions;
    @FXML private Label  statusLabel;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;
    @FXML private Label  pageInfoLabel;

    private static final int PAGE_SIZE        = 20;
    private int              currentPage       = 0;
    private List<BorrowRecord> masterList      = new ArrayList<>();
    private List<BorrowRecord> filteredList    = new ArrayList<>();

    private final BorrowService    borrowService = new BorrowService();
    private final MemberDAO        memberDAO     = new MemberDAO();
    private final BookDAO          bookDAO       = new BookDAO();
    private final FineDAO          fineDAO       = new FineDAO();
    private final NumberFormat     fmt           =
        NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @FXML
    public void initialize() {
        statusFilter.setItems(FXCollections.observableArrayList(
            "Tất cả", "BORROWING", "RETURNED", "OVERDUE", "LOST"));
        statusFilter.setValue("Tất cả");
        setupColumns();
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        statusFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        fromDatePicker.valueProperty().addListener((obs, o, n) -> applyFilter());
        toDatePicker.valueProperty().addListener((obs, o, n) -> applyFilter());
        loadRecords();
    }

    private void setupColumns() {
        colSTT.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(currentPage * PAGE_SIZE + getIndex() + 1));
            }
        });
        colMember.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getMemberCode() + " - " + c.getValue().getMemberName()));
        colBook.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBookTitle()));
        colBorrowDate.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getBorrowDate() != null ? c.getValue().getBorrowDate().toString() : ""));
        colDueDate.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getDueDate() != null ? c.getValue().getDueDate().toString() : ""));
        colReturnDate.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getReturnDate() != null ? c.getValue().getReturnDate().toString() : "—"));
        colNote.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getNotes() != null ? c.getValue().getNotes() : ""));

        // Rich status cell
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                int idx = getIndex();
                if (idx < 0 || idx >= getTableView().getItems().size()) {
                    setGraphic(null); setText(null); return;
                }
                BorrowRecord r = getTableView().getItems().get(idx);
                Label badge = new Label();
                VBox box = new VBox(2, badge);
                switch (item) {
                    case "RETURNED"  -> { badge.setText("Đã trả");  badge.getStyleClass().add("badge-success"); }
                    case "BORROWING" -> { badge.setText("Đang mượn"); badge.getStyleClass().add("badge-info");    }
                    case "OVERDUE"   -> {
                        long days = r.getOverdueDays();
                        badge.setText("Quá hạn"); badge.getStyleClass().add("badge-danger");
                        if (days > 0) {
                            BigDecimal fee = BorrowService.FINE_PER_DAY.multiply(BigDecimal.valueOf(days));
                            Label dLabel = new Label("Quá hạn " + days + " ngày");
                            dLabel.setStyle("-fx-font-size:10px; -fx-text-fill:#B91C1C;");
                            Label fLabel = new Label("Phí phạt: " + fmt.format(fee) + "đ");
                            fLabel.setStyle("-fx-font-size:10px; -fx-text-fill:#D97706;");
                            box.getChildren().addAll(dLabel, fLabel);
                        }
                    }
                    case "LOST" -> {
                        long days = r.getOverdueDays();
                        badge.setText("Mất sách"); badge.getStyleClass().add("badge-warning");
                        Label lLabel = new Label("Đơn giá bồi thường: " + fmt.format(BorrowService.LOST_BOOK_FEE) + "đ");
                        lLabel.setStyle("-fx-font-size:10px; -fx-text-fill:#DC2626;");
                        box.getChildren().add(lLabel);
                        if (days > 0) {
                            BigDecimal fee = BorrowService.FINE_PER_DAY.multiply(BigDecimal.valueOf(days));
                            Label dLabel = new Label("Quá hạn " + days + " ngày");
                            dLabel.setStyle("-fx-font-size:10px; -fx-text-fill:#B91C1C;");
                            Label fLabel = new Label("Phí phạt (ước tính): " + fmt.format(fee) + "đ");
                            fLabel.setStyle("-fx-font-size:10px; -fx-text-fill:#D97706;");
                            box.getChildren().addAll(dLabel, fLabel);
                        }
                    }
                    default -> badge.setText(item);
                }
                box.setStyle("-fx-padding: 2 0 2 0;");
                setGraphic(box);
                setText(null);
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button returnBtn = new Button("Trả sách");
            private final Button extendBtn = new Button("Gia hạn");
            private final Button lostBtn   = new Button("Mất sách");
            private final HBox   box       = new HBox(6, returnBtn, extendBtn, lostBtn);
            {
                returnBtn.getStyleClass().add("btn-success");
                returnBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
                extendBtn.getStyleClass().add("btn-outline");
                extendBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
                lostBtn.getStyleClass().add("btn-danger");
                lostBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
                returnBtn.setOnAction(e -> handleReturn(getTableView().getItems().get(getIndex())));
                extendBtn.setOnAction(e -> handleExtendBorrow(getTableView().getItems().get(getIndex())));
                lostBtn.setOnAction(e  -> handleMarkLost(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                int idx = getIndex();
                if (idx < 0 || idx >= getTableView().getItems().size()) { setGraphic(null); return; }
                BorrowRecord r = getTableView().getItems().get(idx);
                boolean retLost = r.getStatus() == BorrowRecord.Status.BORROWING
                    || r.getStatus() == BorrowRecord.Status.OVERDUE;
                boolean ext     = r.getStatus() == BorrowRecord.Status.BORROWING;
                returnBtn.setVisible(retLost);
                returnBtn.setManaged(retLost);
                lostBtn.setVisible(retLost);
                lostBtn.setManaged(retLost);
                extendBtn.setVisible(ext);
                extendBtn.setManaged(ext);
                setGraphic(retLost || ext ? box : null);
            }
        });
    }

    // ── Filter / Pagination ─────────────────────────────────────────────────

    private void applyFilter() {
        String kw      = searchField.getText().toLowerCase().trim();
        String stVal   = statusFilter.getValue();
        LocalDate from = fromDatePicker.getValue();
        LocalDate to   = toDatePicker.getValue();
        filteredList = masterList.stream()
            .filter(r -> {
                boolean stOk = stVal == null || "Tất cả".equals(stVal)
                    || r.getStatus().name().equals(stVal);
                boolean kwOk = kw.isEmpty()
                    || r.getMemberName().toLowerCase().contains(kw)
                    || r.getMemberCode().toLowerCase().contains(kw)
                    || r.getBookTitle().toLowerCase().contains(kw);
                boolean dateOk = true;
                if (from != null && r.getBorrowDate() != null)
                    dateOk = !r.getBorrowDate().isBefore(from);
                if (to   != null && r.getBorrowDate() != null)
                    dateOk = dateOk && !r.getBorrowDate().isAfter(to);
                return stOk && kwOk && dateOk;
            })
            .sorted(java.util.Comparator
                .comparing(BorrowRecord::getBorrowDate, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                .reversed()
                .thenComparing(BorrowRecord::getId, java.util.Comparator.reverseOrder()))
            .toList();
        currentPage = 0;
        showPage();
    }

    private void showPage() {
        int from = currentPage * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, filteredList.size());
        borrowTable.setItems(FXCollections.observableArrayList(
            from < filteredList.size() ? filteredList.subList(from, to) : List.of()));
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredList.size() / PAGE_SIZE));
        pageInfoLabel.setText("Trang " + (currentPage + 1) + " / " + totalPages);
        statusLabel.setText(filteredList.size() + " phiếu mượn");
        prevPageBtn.setDisable(currentPage == 0);
        nextPageBtn.setDisable((currentPage + 1) * PAGE_SIZE >= filteredList.size());
        borrowTable.refresh();
    }

    @FXML private void prevPage() { if (currentPage > 0) { currentPage--; showPage(); } }
    @FXML private void nextPage() { if ((currentPage + 1) * PAGE_SIZE < filteredList.size()) { currentPage++; showPage(); } }

    @FXML private void handleSearch() { applyFilter(); }

    @FXML
    private void clearDateFilter() {
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
    }

    // ── New Borrow ─────────────────────────────────────────────────────────

    @FXML
    private void handleNewBorrow() {
        List<Member> allMembers = memberDAO.findAll().stream()
            .filter(m -> m.getStatus() == Member.Status.ACTIVE && m.isCardValid())
            .sorted(java.util.Comparator.comparing(Member::getFullName))
            .toList();
        List<Book> allBooks = bookDAO.findAll().stream()
            .filter(Book::isAvailable)
            .sorted(java.util.Comparator.comparing(Book::getTitle))
            .toList();

        Dialog<List<Object>> dialog = new Dialog<>();
        dialog.setTitle("Mượn sách mới");
        dialog.setHeaderText(null);
        ButtonType borrowType = new ButtonType("Xác nhận mượn", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(borrowType, ButtonType.CANCEL);

        ObservableList<Member> memberSrc = FXCollections.observableArrayList(allMembers);
        FilteredList<Member> memFiltered = new FilteredList<>(memberSrc, m -> true);
        TextField memFilter = new TextField();
        memFilter.setPromptText("Mã thẻ, tên đọc giả…");
        ListView<Member> memList = new ListView<>(memFiltered);
        memList.setPrefHeight(120);
        memFilter.textProperty().addListener((o, a, t) -> {
            String s = t == null ? "" : t.toLowerCase();
            memFiltered.setPredicate(m -> s.isEmpty()
                || m.getFullName().toLowerCase().contains(s)
                || m.getMemberCode().toLowerCase().contains(s));
        });
        TextArea memDetail = new TextArea();
        memDetail.setEditable(false);
        memDetail.setPrefRowCount(4);
        memDetail.setWrapText(true);
        memList.getSelectionModel().selectedItemProperty().addListener((o, u, m) -> {
            if (m == null) memDetail.clear();
            else {
                memDetail.setText(
                    "Mã thẻ: " + m.getMemberCode() + "\nHọ tên: " + m.getFullName()
                    + "\nEmail: " + nullToDash(m.getEmail()) + "\nSĐT: " + nullToDash(m.getPhone())
                    + "\nHạn thẻ: " + m.getExpiryDate());
            }
        });

        ObservableList<Book> bookSrc = FXCollections.observableArrayList(allBooks);
        FilteredList<Book> bookFiltered = new FilteredList<>(bookSrc, b -> true);
        TextField bookFilter = new TextField();
        bookFilter.setPromptText("ISBN, tên sách…");
        ListView<Book> bookList = new ListView<>(bookFiltered);
        bookList.setPrefHeight(120);
        bookFilter.textProperty().addListener((o, a, t) -> {
            String s = t == null ? "" : t.toLowerCase();
            bookFiltered.setPredicate(b -> s.isEmpty()
                || b.getTitle().toLowerCase().contains(s)
                || (b.getIsbn() != null && b.getIsbn().toLowerCase().contains(s))
                || b.getAuthor().toLowerCase().contains(s));
        });
        TextArea bookDetail = new TextArea();
        bookDetail.setEditable(false);
        bookDetail.setPrefRowCount(4);
        bookDetail.setWrapText(true);
        bookList.getSelectionModel().selectedItemProperty().addListener((o, u, b) -> {
            if (b == null) bookDetail.clear();
            else {
                bookDetail.setText(
                    "ISBN: " + nullToDash(b.getIsbn()) + "\nTên: " + b.getTitle()
                    + "\nTác giả: " + b.getAuthor()
                    + "\nCòn mượn được: " + b.getAvailableCopies() + " / " + b.getTotalCopies());
            }
        });

        LocalDate minDue = LocalDate.now().plusDays(1);
        LocalDate maxDue = LocalDate.now().plusDays(BorrowService.MAX_LOAN_DAYS);
        DatePicker returnDatePicker = new DatePicker(minDue);
        returnDatePicker.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) {
                    setDisable(true);
                    return;
                }
                setDisable(d.isBefore(minDue) || d.isAfter(maxDue));
            }
        });

        Label hint = new Label("Hạn trả: chọn ngày trong khoảng 1–" + BorrowService.MAX_LOAN_DAYS + " ngày kể từ hôm nay.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill:#64748B; -fx-font-size:11px;");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(16));
        int row = 0;
        grid.add(new Label("Đọc giả *"), 0, row);
        grid.add(memFilter, 1, row++);
        grid.add(memList, 0, row++, 2, 1);
        grid.add(memDetail, 0, row++, 2, 1);
        grid.add(new Label("Sách *"), 0, row);
        grid.add(bookFilter, 1, row++);
        grid.add(bookList, 0, row++, 2, 1);
        grid.add(bookDetail, 0, row++, 2, 1);
        grid.add(new Label("Ngày trả dự kiến *"), 0, row);
        grid.add(returnDatePicker, 1, row++);
        grid.add(hint, 0, row++, 2, 1);

        Label formError = new Label();
        formError.setVisible(false);
        formError.setManaged(false);
        formError.setWrapText(true);
        formError.setMaxWidth(400);
        formError.setStyle("-fx-text-fill: #E11D48;");
        grid.add(formError, 0, row, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        Button borrowBtn = (Button) dialog.getDialogPane().lookupButton(borrowType);
        borrowBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            List<String> errs = new ArrayList<>();
            if (memList.getSelectionModel().getSelectedItem() == null) errs.add("• Chọn đọc giả.");
            if (bookList.getSelectionModel().getSelectedItem() == null) errs.add("• Chọn sách.");
            LocalDate due = returnDatePicker.getValue();
            if (due == null || !due.isAfter(LocalDate.now())) errs.add("• Chọn ngày trả sau hôm nay.");
            if (!errs.isEmpty()) {
                formError.setText(String.join("\n", errs));
                formError.setVisible(true);
                formError.setManaged(true);
                evt.consume();
            }
        });

        dialog.setResultConverter(bt -> {
            if (bt != borrowType) return null;
            Member sm = memList.getSelectionModel().getSelectedItem();
            Book bk = bookList.getSelectionModel().getSelectedItem();
            LocalDate due = returnDatePicker.getValue();
            if (sm == null || bk == null || due == null) return null;
            return List.<Object>of(sm.getId(), bk.getId(), due);
        });

        Optional<List<Object>> res = dialog.showAndWait();
        res.ifPresent(list -> {
            try {
                int mid = (Integer) list.get(0);
                int bid = (Integer) list.get(1);
                LocalDate due = (LocalDate) list.get(2);
                BorrowRecord record = borrowService.borrowBook(mid, bid, due);
                loadRecords();
                showInfo("Mượn sách thành công!\nHạn trả: " + record.getDueDate());
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private void handleExtendBorrow(BorrowRecord record) {
        LocalDate minDue = record.getDueDate().plusDays(1);
        LocalDate maxDue = record.getBorrowDate().plusDays(BorrowService.MAX_LOAN_DAYS);
        DatePicker dp = new DatePicker(minDue);
        dp.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) {
                    setDisable(true);
                    return;
                }
                setDisable(d.isBefore(minDue) || d.isAfter(maxDue) || d.isBefore(LocalDate.now()));
            }
        });
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Gia hạn mượn sách");
        dlg.setHeaderText("Phiếu: " + record.getBookTitle());
        ButtonType ok = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(16));
        g.add(new Label("Hạn trả hiện tại:"), 0, 0);
        g.add(new Label(record.getDueDate().toString()), 1, 0);
        g.add(new Label("Hạn trả mới:"), 0, 1);
        g.add(dp, 1, 1);
        Label cap = new Label("Không vượt quá " + BorrowService.MAX_LOAN_DAYS + " ngày kể từ ngày mượn.");
        cap.setStyle("-fx-font-size:11px; -fx-text-fill:#64748B;");
        g.add(cap, 0, 2, 2, 1);
        dlg.getDialogPane().setContent(g);
        Optional<ButtonType> out = dlg.showAndWait();
        if (out.isEmpty() || out.get() != ok) return;
        LocalDate nd = dp.getValue();
        if (nd == null) return;
        try {
            borrowService.extendBorrow(record.getId(), nd);
            loadRecords();
            showInfo("Đã gia hạn. Hạn trả mới: " + nd);
        } catch (Exception e) { showError(e.getMessage()); }
    }

    // ── Return / Lost ──────────────────────────────────────────────────────

    private void handleReturn(BorrowRecord record) {
        long overdueDays = record.getOverdueDays();
        String detail = overdueDays > 0
            ? "\nQuá hạn " + overdueDays + " ngày. Dự kiến phạt: "
              + fmt.format(BorrowService.FINE_PER_DAY.multiply(BigDecimal.valueOf(overdueDays))) + "đ"
            : " (trả đúng hạn)";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Trả sách \"" + record.getBookTitle() + "\"?" + detail,
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Xác nhận trả sách");
        confirm.showAndWait().ifPresent(t -> {
            if (t != ButtonType.YES) return;
            try {
                Optional<Fine> fine = borrowService.returnBook(record.getId());
                loadRecords();
                if (fine.isPresent()) {
                    Fine f = fine.get();
                    // Step 2: offer to pay fine immediately
                    Alert payDialog = new Alert(Alert.AlertType.CONFIRMATION);
                    payDialog.setTitle("Thu phí phạt");
                    payDialog.setHeaderText("Phiếu phạt quá hạn");
                    payDialog.setContentText(
                        "Số tiền: " + fmt.format(f.getAmount()) + "đ\n"
                        + "Lý do: " + f.getReason() + "\n\nThu phí phạt ngay?");
                    ButtonType payNow   = new ButtonType("Đã thu", ButtonBar.ButtonData.YES);
                    ButtonType payLater = new ButtonType("Sau", ButtonBar.ButtonData.NO);
                    payDialog.getButtonTypes().setAll(payNow, payLater);
                    payDialog.showAndWait().ifPresent(p -> {
                        if (p == payNow) {
                            fineDAO.markAsPaid(f.getId());
                            showInfo("Trả sách thành công. Đã thu phí phạt "
                                     + fmt.format(f.getAmount()) + "đ.");
                        } else {
                            showInfo("Trả sách thành công. Phí phạt chưa thu.");
                        }
                    });
                } else {
                    showInfo("Trả sách thành công!");
                }
            } catch (Exception e) { showError(e.getMessage()); }
        });
    }

    private void handleMarkLost(BorrowRecord record) {
        long days = record.getOverdueDays();
        String feeStr = days > 0
            ? "\nPhạt trễ: " + fmt.format(BorrowService.FINE_PER_DAY.multiply(BigDecimal.valueOf(days))) + "đ"
            : "";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Đánh dấu sách \"" + record.getBookTitle() + "\" là MẤT?\n"
            + "Tổng thu (ước tính): phí quá hạn (nếu có) + bồi thường 200.000đ." + feeStr,
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Xác nhận mất sách");
        confirm.showAndWait().ifPresent(t -> {
            if (t != ButtonType.YES) return;
            try {
                Fine fine = borrowService.markAsLost(record.getId());
                loadRecords();
                // Step 2: offer to pay fine immediately
                Alert payDialog = new Alert(Alert.AlertType.CONFIRMATION);
                payDialog.setTitle("Thu phí bồi thường");
                payDialog.setHeaderText("Phiếu bồi thường sách mất");
                payDialog.setContentText(
                    "Số tiền: " + fmt.format(fine.getAmount()) + "đ\n"
                    + "Lý do: " + fine.getReason() + "\n\nThu tiền bồi thường ngay?");
                ButtonType payNow   = new ButtonType("Đã thu", ButtonBar.ButtonData.YES);
                ButtonType payLater = new ButtonType("Sau", ButtonBar.ButtonData.NO);
                payDialog.getButtonTypes().setAll(payNow, payLater);
                payDialog.showAndWait().ifPresent(p -> {
                    if (p == payNow) {
                        fineDAO.markAsPaid(fine.getId());
                        showInfo("Đã ghi nhận mất sách. Đã thu bồi thường "
                                 + fmt.format(fine.getAmount()) + "đ.");
                    } else {
                        showInfo("Đã ghi nhận mất sách. Tiền bồi thường chưa thu.");
                    }
                });
            } catch (Exception e) { showError(e.getMessage()); }
        });
    }

    @FXML
    private void handleSyncOverdue() {
        int count = borrowService.syncOverdueStatuses();
        loadRecords();
        showInfo("Đã cập nhật " + count + " phiếu mượn quá hạn.");
    }

    private void loadRecords() {
        borrowService.syncOverdueStatuses();
        borrowService.syncLongOverdueToLost();
        masterList = borrowService.getAllRecords();
        applyFilter();
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
