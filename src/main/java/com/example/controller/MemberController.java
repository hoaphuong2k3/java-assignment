package com.example.controller;

import com.example.model.Fine;
import com.example.model.Member;
import com.example.service.MemberService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MemberController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<Member> memberTable;
    @FXML private TableColumn<Member, Void>   colSTT;
    @FXML private TableColumn<Member, String> colCode;
    @FXML private TableColumn<Member, String> colName;
    @FXML private TableColumn<Member, String> colPhone;
    @FXML private TableColumn<Member, String> colEmail;
    @FXML private TableColumn<Member, String> colExpiry;
    @FXML private TableColumn<Member, String> colStatus;
    @FXML private TableColumn<Member, Void>   colActions;
    @FXML private Label  statusLabel;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;
    @FXML private Label  pageInfoLabel;

    private static final int PAGE_SIZE   = 20;
    private int            currentPage   = 0;
    private List<Member>   masterList    = new ArrayList<>();
    private List<Member>   filteredList  = new ArrayList<>();

    private final MemberService memberService = new MemberService();
    private final com.example.dao.FineDAO fineDAO = new com.example.dao.FineDAO();
    private final NumberFormat moneyFmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @FXML
    public void initialize() {
        statusFilter.setItems(FXCollections.observableArrayList(
            "Tất cả", "Hoạt động", "Hết hạn", "Đình chỉ"));
        statusFilter.setValue("Tất cả");
        setupColumns();
        memberService.syncExpiredStatuses();
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        statusFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        loadMembers();
    }

    private void setupColumns() {
        colSTT.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(currentPage * PAGE_SIZE + getIndex() + 1));
            }
        });
        colCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMemberCode()));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        colPhone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPhone()));
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        colExpiry.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getExpiryDate() != null ? c.getValue().getExpiryDate().toString() : ""));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(statusVietnamese(c.getValue().getStatus())));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().clear();
                switch (item) {
                    case "Hoạt động" -> badge.getStyleClass().add("badge-success");
                    case "Hết hạn"   -> badge.getStyleClass().add("badge-warning");
                    case "Đình chỉ"  -> badge.getStyleClass().add("badge-danger");
                }
                setGraphic(badge);
            }
        });
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("Sửa");
            private final Button renewBtn  = new Button("Gia hạn");
            private final Button deleteBtn = new Button("Xóa");
            private final Button unlockBtn = new Button("Mở khóa");
            private final HBox   box       = new HBox(6, editBtn, renewBtn, deleteBtn, unlockBtn);
            {
                for (Button b : new Button[]{ editBtn, renewBtn, deleteBtn, unlockBtn }) {
                    b.getStyleClass().add("btn-outline");
                    b.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
                }
                renewBtn.getStyleClass().setAll("btn-warning");
                renewBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
                unlockBtn.getStyleClass().setAll("btn-success");
                editBtn.setOnAction(e -> handleEdit(getTableView().getItems().get(getIndex())));
                renewBtn.setOnAction(e -> handleRenew(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
                unlockBtn.setOnAction(e -> handleUnlock(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                int idx = getIndex();
                if (idx < 0 || idx >= getTableView().getItems().size()) { setGraphic(null); return; }
                Member m = getTableView().getItems().get(idx);
                boolean expired = m.getStatus() == Member.Status.EXPIRED;
                boolean suspended = m.getStatus() == Member.Status.SUSPENDED;
                renewBtn.setVisible(expired);
                renewBtn.setManaged(expired);
                deleteBtn.setVisible(expired);
                deleteBtn.setManaged(expired);
                unlockBtn.setVisible(suspended);
                unlockBtn.setManaged(suspended);
                setGraphic(box);
            }
        });
    }

    @FXML
    private void handleSearch() { applyFilter(); }

    private static String statusVietnamese(Member.Status s) {
        return switch (s) {
            case ACTIVE -> "Hoạt động";
            case EXPIRED -> "Hết hạn";
            case SUSPENDED -> "Đình chỉ";
        };
    }

    private static boolean matchesMemberStatusFilter(Member m, String uiStatus) {
        if (uiStatus == null || "Tất cả".equals(uiStatus)) return true;
        return switch (uiStatus) {
            case "Hoạt động" -> m.getStatus() == Member.Status.ACTIVE;
            case "Hết hạn" -> m.getStatus() == Member.Status.EXPIRED;
            case "Đình chỉ" -> m.getStatus() == Member.Status.SUSPENDED;
            default -> m.getStatus().name().equals(uiStatus);
        };
    }

    private void applyFilter() {
        String kw = searchField.getText().toLowerCase().trim();
        String statusVal = statusFilter.getValue();
        filteredList = masterList.stream()
            .filter(m -> {
                boolean statusOk = matchesMemberStatusFilter(m, statusVal);
                String email = m.getEmail() != null ? m.getEmail().toLowerCase() : "";
                String phone = m.getPhone() != null ? m.getPhone().toLowerCase() : "";
                boolean kwOk = kw.isEmpty()
                    || m.getFullName().toLowerCase().contains(kw)
                    || m.getMemberCode().toLowerCase().contains(kw)
                    || phone.contains(kw)
                    || email.contains(kw)
                    || statusVietnamese(m.getStatus()).toLowerCase().contains(kw);
                return statusOk && kwOk;
            })
            .sorted(java.util.Comparator.comparing(Member::getFullName))
            .toList();
        currentPage = 0;
        showPage();
    }

    private void showPage() {
        int from = currentPage * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, filteredList.size());
        memberTable.setItems(FXCollections.observableArrayList(
            from < filteredList.size() ? filteredList.subList(from, to) : List.of()));
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredList.size() / PAGE_SIZE));
        pageInfoLabel.setText("Trang " + (currentPage + 1) + " / " + totalPages);
        statusLabel.setText(filteredList.size() + " đọc giả");
        prevPageBtn.setDisable(currentPage == 0);
        nextPageBtn.setDisable((currentPage + 1) * PAGE_SIZE >= filteredList.size());
        memberTable.refresh();
    }

    @FXML private void prevPage() { if (currentPage > 0) { currentPage--; showPage(); } }
    @FXML private void nextPage() { if ((currentPage + 1) * PAGE_SIZE < filteredList.size()) { currentPage++; showPage(); } }

    private void loadMembers() {
        masterList = memberService.getAllMembers();
        applyFilter();
    }

    @FXML
    private void handleAdd() {
        showNewMemberDialog();
    }

    private void handleEdit(Member member) {
        showEditMemberDialog(member);
    }

    private void handleRenew(Member member) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Gia hạn thẻ cho \"" + member.getFullName() + "\" với phí "
                + moneyFmt.format(MemberService.RENEW_CARD_FEE) + "đ?\n"
                + "Thời hạn mới: " + MemberService.MEMBERSHIP_EXTENSION_YEARS + " năm kể từ hôm nay.",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Gia hạn thẻ đọc giả");
        confirm.showAndWait().ifPresent(t -> {
            if (t != ButtonType.YES) return;
            try {
                Fine fine = memberService.renewExpiredCardWithFee(member.getId());
                loadMembers();
                showPayFineDialog(fine, "Thu phí gia hạn thẻ");
            } catch (Exception e) { showError(e.getMessage()); }
        });
    }

    private void handleUnlock(Member member) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Mở khóa thẻ cho \"" + member.getFullName() + "\" với phí "
                + moneyFmt.format(MemberService.UNLOCK_CARD_FEE) + "đ?\n"
                + "Bộ đếm mất sách sẽ được reset.",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Mở khóa thẻ");
        confirm.showAndWait().ifPresent(t -> {
            if (t != ButtonType.YES) return;
            try {
                Fine fine = memberService.unlockSuspendedCardWithFee(member.getId());
                loadMembers();
                showPayFineDialog(fine, "Thu phí mở khóa thẻ");
            } catch (Exception e) { showError(e.getMessage()); }
        });
    }

    private void showPayFineDialog(Fine fine, String title) {
        Alert payDialog = new Alert(Alert.AlertType.CONFIRMATION);
        payDialog.setTitle(title);
        payDialog.setHeaderText(null);
        payDialog.setContentText(
            "Số tiền: " + moneyFmt.format(fine.getAmount()) + "đ\n"
            + "Lý do: " + fine.getReason() + "\n\nĐã thu tiền?");
        ButtonType payNow   = new ButtonType("Đã thu", ButtonBar.ButtonData.YES);
        ButtonType payLater = new ButtonType("Chưa thu", ButtonBar.ButtonData.NO);
        payDialog.getButtonTypes().setAll(payNow, payLater);
        payDialog.showAndWait().ifPresent(p -> {
            if (p == payNow) {
                fineDAO.markAsPaid(fine.getId());
                showInfo("Đã ghi nhận thanh toán.");
            } else {
                showInfo("Phí chưa thu — có thể thu sau tại mục Phí phạt.");
            }
        });
    }

    private void handleDelete(Member member) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Xóa đọc giả \"" + member.getFullName() + "\" khỏi danh sách?",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Xác nhận xóa đọc giả");
        confirm.showAndWait().ifPresent(t -> {
            if (t == ButtonType.YES) {
                try {
                    memberService.deleteMember(member.getId());
                    loadMembers();
                    showInfo("Đã xóa đọc giả khỏi danh sách.");
                } catch (Exception e) { showError(e.getMessage()); }
            }
        });
    }

    private void showNewMemberDialog() {
        Dialog<Member> dialog = new Dialog<>();
        dialog.setTitle("Thêm đọc giả mới");
        dialog.setHeaderText(null);
        ButtonType saveType = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField nameField  = new TextField();
        TextField emailField = new TextField();
        TextField phoneField = new TextField();
        TextArea  addrField  = new TextArea();
        addrField.setPrefRowCount(2);

        grid.add(new Label("Họ tên *:"),       0, 0); grid.add(nameField,  1, 0);
        grid.add(new Label("Email:"),          0, 1); grid.add(emailField, 1, 1);
        grid.add(new Label("Số điện thoại:"), 0, 2); grid.add(phoneField, 1, 2);
        grid.add(new Label("Địa chỉ:"),       0, 3); grid.add(addrField,  1, 3);
        Label hint = new Label("Thẻ đọc: miễn phí " + MemberService.MEMBERSHIP_EXTENSION_YEARS
            + " năm kể từ ngày đăng ký.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill:#64748B; -fx-font-size:12px;");
        grid.add(hint, 0, 4, 2, 1);

        Label formError = new Label();
        formError.setVisible(false);
        formError.setManaged(false);
        formError.setWrapText(true);
        formError.setMaxWidth(320);
        formError.setStyle("-fx-text-fill: #E11D48; -fx-font-size: 12px;");
        grid.add(formError, 0, 5, 2, 1);

        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            List<String> errs = validateMemberForm(nameField, emailField, phoneField);
            if (!errs.isEmpty()) {
                formError.setText(String.join("\n", errs));
                formError.setVisible(true);
                formError.setManaged(true);
                evt.consume();
            }
        });

        dialog.setResultConverter(bt -> {
            if (bt != saveType) return null;
            Member m = new Member();
            m.setFullName(nameField.getText().trim());
            m.setEmail(emailField.getText().trim());
            m.setPhone(phoneField.getText().trim());
            m.setAddress(addrField.getText().trim());
            return m;
        });

        dialog.showAndWait().ifPresent(m -> {
            try {
                memberService.addMember(m);
                loadMembers();
                showInfo("Thêm đọc giả thành công. Mã thẻ: " + m.getMemberCode());
            } catch (Exception e) { showError(e.getMessage()); }
        });
    }

    private void showEditMemberDialog(Member member) {
        Dialog<Member> dialog = new Dialog<>();
        dialog.setTitle("Cập nhật đọc giả");
        dialog.setHeaderText(null);
        ButtonType saveType = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField nameField  = new TextField(member.getFullName());
        TextField emailField = new TextField(member.getEmail());
        TextField phoneField = new TextField(member.getPhone());
        TextArea  addrField  = new TextArea(member.getAddress() != null ? member.getAddress() : "");
        addrField.setPrefRowCount(2);

        int row = 0;
        grid.add(new Label("Họ tên *:"), 0, row); grid.add(nameField, 1, row++);
        grid.add(new Label("Email:"), 0, row); grid.add(emailField, 1, row++);
        grid.add(new Label("Số điện thoại:"), 0, row); grid.add(phoneField, 1, row++);
        grid.add(new Label("Địa chỉ:"), 0, row); grid.add(addrField, 1, row++);

        Label hint = new Label();
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill:#64748B; -fx-font-size:12px;");
        if (member.getStatus() == Member.Status.SUSPENDED) {
            hint.setText("Thẻ đang khóa — chỉ sửa thông tin liên hệ. Để mở khóa: dùng nút \"Mở khóa\" và đóng phí.");
        } else if (member.getStatus() == Member.Status.EXPIRED || member.getStatus() == Member.Status.ACTIVE) {
            hint.setText("Hạn thẻ và trạng thái chỉ thay đổi qua gia hạn / hệ thống (không sửa trực tiếp).");
        }
        grid.add(hint, 0, row++, 2, 1);

        Label roExpiry = new Label("Hạn thẻ: " + member.getExpiryDate());
        roExpiry.setStyle("-fx-font-weight: bold;");
        grid.add(roExpiry, 0, row++, 2, 1);
        Label roStatus = new Label("Trạng thái: " + statusVietnamese(member.getStatus()));
        grid.add(roStatus, 0, row++, 2, 1);

        Label formError = new Label();
        formError.setVisible(false);
        formError.setManaged(false);
        formError.setWrapText(true);
        formError.setMaxWidth(320);
        formError.setStyle("-fx-text-fill: #E11D48; -fx-font-size: 12px;");
        grid.add(formError, 0, row, 2, 1);

        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            List<String> errs = validateMemberForm(nameField, emailField, phoneField);
            if (!errs.isEmpty()) {
                formError.setText(String.join("\n", errs));
                formError.setVisible(true);
                formError.setManaged(true);
                evt.consume();
            }
        });

        int memberId = member.getId();
        dialog.setResultConverter(bt -> {
            if (bt != saveType) return null;
            Member m = new Member();
            m.setId(memberId);
            m.setFullName(nameField.getText().trim());
            m.setEmail(emailField.getText().trim());
            m.setPhone(phoneField.getText().trim());
            m.setAddress(addrField.getText().trim());
            return m;
        });

        dialog.showAndWait().ifPresent(m -> {
            try {
                memberService.updateMember(m);
                loadMembers();
                showInfo("Cập nhật thành công.");
            } catch (Exception e) { showError(e.getMessage()); }
        });
    }

    private List<String> validateMemberForm(TextField nameField, TextField emailField, TextField phoneField) {
        List<String> errs = new ArrayList<>();
        if (nameField.getText().isBlank())
            errs.add("• Họ tên không được để trống.");
        String email = emailField.getText().trim();
        if (!email.isEmpty() && !email.matches("^[\\w+.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"))
            errs.add("• Email không đúng định dạng.");
        String phone = phoneField.getText().trim();
        if (!phone.isEmpty() && !phone.matches("^(0|\\+84)\\d{9,10}$"))
            errs.add("• Số điện thoại không hợp lệ (0 hoặc +84, 10–11 chữ số).");
        return errs;
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
