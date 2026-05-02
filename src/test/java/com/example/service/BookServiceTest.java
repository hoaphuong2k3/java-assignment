package com.example.service;

import com.example.dao.BookDAO;
import com.example.model.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookDAO bookDAO;

    @InjectMocks
    private BookService bookService;

    private Book validBook;

    @BeforeEach
    void setUp() {
        validBook = new Book();
        validBook.setId(1);
        validBook.setTitle("Clean Code");
        validBook.setAuthor("Robert Martin");
        validBook.setCategoryId(1);
        validBook.setPublisher("Prentice Hall");
        validBook.setTotalCopies(3);
        validBook.setAvailableCopies(3);

        lenient().when(bookDAO.existsIsbnForOtherBook(anyString(), nullable(Integer.class))).thenReturn(false);
        lenient().when(bookDAO.countBorrowingOrOverdue(anyInt())).thenReturn(0);
    }

    // --- getAllBooks ---

    @Test
    void getAllBooks_returnsDaoResult() {
        when(bookDAO.findAll()).thenReturn(List.of(validBook));
        List<Book> result = bookService.getAllBooks();
        assertEquals(1, result.size());
        verify(bookDAO).findAll();
    }

    // --- searchBooks ---

    @Test
    void searchBooks_blankKeyword_returnsAll() {
        when(bookDAO.findAll()).thenReturn(List.of(validBook));
        List<Book> result = bookService.searchBooks("  ");
        assertEquals(1, result.size());
        verify(bookDAO).findAll();
        verify(bookDAO, never()).search(any());
    }

    @Test
    void searchBooks_withKeyword_delegatesToDaoSearch() {
        when(bookDAO.search("clean")).thenReturn(List.of(validBook));
        List<Book> result = bookService.searchBooks("clean");
        assertEquals(1, result.size());
        verify(bookDAO).search("clean");
    }

    @Test
    void searchBooks_nullKeyword_returnsAll() {
        when(bookDAO.findAll()).thenReturn(List.of(validBook));
        List<Book> result = bookService.searchBooks(null);
        assertNotNull(result);
        verify(bookDAO).findAll();
    }

    // --- addBook ---

    @Test
    void addBook_validBook_savesAndSetsAvailableCopies() {
        Book toAdd = new Book();
        toAdd.setTitle("Design Patterns");
        toAdd.setAuthor("GoF");
        toAdd.setCategoryId(2);
        toAdd.setPublisher("Addison-Wesley");
        toAdd.setTotalCopies(5);

        bookService.addBook(toAdd);

        assertEquals(5, toAdd.getAvailableCopies());
        verify(bookDAO).save(toAdd);
    }

    @Test
    void addBook_emptyTitle_throwsIllegalArgument() {
        Book bad = new Book();
        bad.setTitle("  ");
        bad.setAuthor("Someone");
        bad.setTotalCopies(1);

        assertThrows(IllegalArgumentException.class, () -> bookService.addBook(bad));
        verify(bookDAO, never()).save(any());
    }

    @Test
    void addBook_emptyAuthor_throwsIllegalArgument() {
        Book bad = new Book();
        bad.setTitle("Some Book");
        bad.setAuthor("");
        bad.setTotalCopies(1);

        assertThrows(IllegalArgumentException.class, () -> bookService.addBook(bad));
        verify(bookDAO, never()).save(any());
    }

    @Test
    void addBook_noCategory_throws() {
        Book bad = new Book();
        bad.setTitle("X");
        bad.setAuthor("Y");
        bad.setPublisher("Z");
        bad.setTotalCopies(1);
        assertThrows(IllegalArgumentException.class, () -> bookService.addBook(bad));
        verify(bookDAO, never()).save(any());
    }

    @Test
    void addBook_noPublisher_throws() {
        Book bad = new Book();
        bad.setTitle("X");
        bad.setAuthor("Y");
        bad.setCategoryId(1);
        bad.setTotalCopies(1);
        assertThrows(IllegalArgumentException.class, () -> bookService.addBook(bad));
        verify(bookDAO, never()).save(any());
    }

    @Test
    void addBook_duplicateIsbn_throws() {
        when(bookDAO.existsIsbnForOtherBook(eq("DUP-ISBN"), isNull())).thenReturn(true);
        Book b = new Book();
        b.setTitle("T");
        b.setAuthor("A");
        b.setCategoryId(1);
        b.setPublisher("P");
        b.setTotalCopies(1);
        assertThrows(IllegalArgumentException.class, () -> bookService.addBook(b));
        verify(bookDAO, never()).save(any());
    }

    @Test
    void addBook_zeroCopies_throwsIllegalArgument() {
        Book bad = new Book();
        bad.setTitle("Some Book");
        bad.setAuthor("Author");
        bad.setCategoryId(1);
        bad.setPublisher("NXB");
        bad.setTotalCopies(0);

        assertThrows(IllegalArgumentException.class, () -> bookService.addBook(bad));
        verify(bookDAO, never()).save(any());
    }

    // --- updateBook ---

    @Test
    void updateBook_valid_updatesAvailableCopiesAccordingToBorrowedCount() {
        // existing: total=3, available=1 → borrowed=2
        Book existing = new Book();
        existing.setId(1);
        existing.setTitle("Clean Code");
        existing.setAuthor("Robert Martin");
        existing.setCategoryId(1);
        existing.setPublisher("PH");
        existing.setTotalCopies(3);
        existing.setAvailableCopies(1);

        when(bookDAO.findById(1)).thenReturn(Optional.of(existing));
        when(bookDAO.countCopiesOut(1)).thenReturn(2);

        // update: new total=5
        Book update = new Book();
        update.setId(1);
        update.setTitle("Clean Code");
        update.setAuthor("Robert Martin");
        update.setCategoryId(1);
        update.setPublisher("PH");
        update.setTotalCopies(5);

        bookService.updateBook(update);

        // borrowed=2, so new available = 5-2 = 3
        assertEquals(3, update.getAvailableCopies());
        verify(bookDAO).update(update);
    }

    @Test
    void updateBook_newTotalLessThanBorrowed_throwsIllegalArgument() {
        // existing: total=3, available=0 → borrowed=3
        Book existing = new Book();
        existing.setId(1);
        existing.setTitle("Clean Code");
        existing.setAuthor("Robert Martin");
        existing.setCategoryId(1);
        existing.setPublisher("PH");
        existing.setTotalCopies(3);
        existing.setAvailableCopies(0);

        when(bookDAO.findById(1)).thenReturn(Optional.of(existing));
        when(bookDAO.countCopiesOut(1)).thenReturn(3);

        Book update = new Book();
        update.setId(1);
        update.setTitle("Clean Code");
        update.setAuthor("Robert Martin");
        update.setCategoryId(1);
        update.setPublisher("PH");
        update.setTotalCopies(2); // less than borrowed (3)

        assertThrows(IllegalArgumentException.class, () -> bookService.updateBook(update));
        verify(bookDAO, never()).update(any());
    }

    @Test
    void updateBook_nonExistentId_throwsIllegalArgument() {
        when(bookDAO.findById(99)).thenReturn(Optional.empty());

        Book update = new Book();
        update.setId(99);
        update.setTitle("Ghost");
        update.setAuthor("Author");
        update.setCategoryId(1);
        update.setPublisher("P");
        update.setTotalCopies(1);

        assertThrows(IllegalArgumentException.class, () -> bookService.updateBook(update));
        verify(bookDAO, never()).update(any());
    }

    // --- deleteBook ---

    @Test
    void deleteBook_existingBook_callsSoftDelete() {
        when(bookDAO.findById(1)).thenReturn(Optional.of(validBook));

        bookService.deleteBook(1);

        verify(bookDAO).countBorrowingOrOverdue(1);
        verify(bookDAO).softDelete(1);
    }

    @Test
    void deleteBook_whenBorrowingOrOverdue_throws() {
        when(bookDAO.findById(1)).thenReturn(Optional.of(validBook));
        when(bookDAO.countBorrowingOrOverdue(1)).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> bookService.deleteBook(1));
        verify(bookDAO, never()).softDelete(anyInt());
    }

    @Test
    void deleteBook_nonExistent_throwsIllegalArgument() {
        when(bookDAO.findById(42)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> bookService.deleteBook(42));
        verify(bookDAO, never()).softDelete(anyInt());
    }

    // --- getBooksByCategory ---

    @Test
    void getBooksByCategory_delegatesToDao() {
        when(bookDAO.findByCategory(3)).thenReturn(List.of(validBook));
        List<Book> result = bookService.getBooksByCategory(3);
        assertEquals(1, result.size());
        verify(bookDAO).findByCategory(3);
    }
}
