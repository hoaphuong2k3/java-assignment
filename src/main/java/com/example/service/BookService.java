package com.example.service;

import com.example.dao.BookDAO;
import com.example.model.Book;

import java.util.List;
import java.util.Optional;

public class BookService {

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

    /** Smoke delete: đánh dấu ẩn, không xóa hàng DB (giữ lịch sử mượn). */
    public void deleteBook(int id) {
        bookDAO.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Sách không tồn tại hoặc đã được ẩn."));
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
        if (book.getTotalCopies() < 1)
            throw new IllegalArgumentException("Số lượng sách phải lớn hơn 0.");
    }
}
