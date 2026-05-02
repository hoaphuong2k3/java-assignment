-- Chạy trên DB đã tạo từ bản schema cũ (trước khi có lost_book_count / fines.member_id)

USE library_db;

ALTER TABLE members
  ADD COLUMN lost_book_count INT NOT NULL DEFAULT 0 AFTER status;

ALTER TABLE fines
  MODIFY borrow_record_id INT NULL;

ALTER TABLE fines
  ADD COLUMN member_id INT NULL AFTER borrow_record_id,
  ADD CONSTRAINT fk_fine_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
  ADD CONSTRAINT chk_fine_has_ref CHECK (borrow_record_id IS NOT NULL OR member_id IS NOT NULL);
