package com.example.service;

import com.example.dao.BookDAO;
import com.example.model.Book;

import java.time.Year;
import java.util.List;
import java.util.Optional;

public class BookService {

    public static final int PUBLISH_YEAR_MIN = 1000;

    /** Cho phép tối đa năm sau (in sớm); tránh typo kiểu 22025. */
    public static int publishYearMaxInclusive() {
        return Year.now().getValue() + 1;
    }

    private final BookDAO bookDAO;

    public BookService() {
        this.bookDAO = new BookDAO();
    }

    /** Package-private constructor for testing. */
    BookService(BookDAO bookDAO) {
        this.bookDAO = bookDAO;
    }

    public List<Book> getAllBooks() {
        return bookDAO.findAll();
    }

    public List<Book> searchBooks(String keyword) {
        if (keyword == null || keyword.isBlank()) return bookDAO.findAll();
        return bookDAO.search(keyword.trim());
    }

    public List<Book> getBooksByCategory(int categoryId) {
        return bookDAO.findByCategory(categoryId);
    }

    public Optional<Book> getBookById(int id) {
        return bookDAO.findById(id);
    }

    public void addBook(Book book) {
        validateBook(book);
        book.setAvailableCopies(book.getTotalCopies());
        bookDAO.save(book);
    }

    public void updateBook(Book book) {
        validateBook(book);
        bookDAO.findById(book.getId())
            .orElseThrow(() -> new IllegalArgumentException("Sách không tồn tại."));
        int borrowed = bookDAO.countCopiesOut(book.getId());
        if (book.getTotalCopies() < borrowed) {
            throw new IllegalArgumentException(
                "Số lượng mới (" + book.getTotalCopies() + ") thấp hơn số đang được mượn (" + borrowed + ").");
        }
        book.setAvailableCopies(book.getTotalCopies() - borrowed);
        bookDAO.update(book);
    }

    /** Smoke delete: đánh dấu ẩn. Không cho xóa khi còn phiếu đang mượn/quá hạn; sách mất (LOST) vẫn xóa được. */
    public void deleteBook(int id) {
        bookDAO.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Sách không tồn tại hoặc đã được ẩn."));
        int active = bookDAO.countBorrowingOrOverdue(id);
        if (active > 0) {
            throw new IllegalStateException(
                "Không thể xóa sách đang được mượn hoặc quá hạn chưa trả (" + active + " phiếu). "
                    + "Chỉ có thể ẩn khi không còn phiếu đang mượn (trừ trường hợp đã ghi nhận mất sách).");
        }
        bookDAO.softDelete(id);
    }

    public long getTotalBooks() {
        return bookDAO.countTotal();
    }

    private void validateBook(Book book) {
        if (book.getTitle() == null || book.getTitle().isBlank())
            throw new IllegalArgumentException("Tên sách không được để trống.");
        if (book.getAuthor() == null || book.getAuthor().isBlank())
            throw new IllegalArgumentException("Tác giả không được để trống.");
        if (book.getCategoryId() == null)
            throw new IllegalArgumentException("Vui lòng chọn danh mục sách.");
        if (book.getPublisher() == null || book.getPublisher().isBlank())
            throw new IllegalArgumentException("Nhà xuất bản (NXB) không được để trống.");
        if (book.getTotalCopies() < 1)
            throw new IllegalArgumentException("Số lượng sách phải lớn hơn 0.");
        Integer py = book.getPublishYear();
        if (py != null) {
            int max = publishYearMaxInclusive();
            if (py < PUBLISH_YEAR_MIN || py > max) {
                throw new IllegalArgumentException(
                    "Năm xuất bản phải từ " + PUBLISH_YEAR_MIN + " đến " + max
                        + " (4 chữ số), hoặc để trống.");
            }
        }
        String isbn = book.getIsbn();
        if (isbn != null && !isbn.isBlank()) {
            Integer exclude = book.getId() > 0 ? book.getId() : null;
            if (bookDAO.existsIsbnForOtherBook(isbn, exclude))
                throw new IllegalArgumentException("ISBN đã tồn tại cho sách khác. Vui lòng kiểm tra lại.");
        }
    }
}
