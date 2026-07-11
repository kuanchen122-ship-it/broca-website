create table if not exists users (
    id bigint auto_increment primary key,
    username varchar(64) not null unique,
    password_hash varchar(255) not null,
    display_name varchar(100) not null,
    role varchar(20) not null,
    enabled boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table if not exists students (
    id bigint auto_increment primary key,
    student_no varchar(32) not null unique,
    chinese_name varchar(80) not null,
    english_name varchar(80),
    school varchar(80),
    grade_level varchar(40),
    parent_name varchar(80),
    parent_phone varchar(40),
    parent_line_id varchar(128),
    pickup_note varchar(500),
    emergency_contact_name varchar(80),
    emergency_contact_phone varchar(40),
    import_source varchar(40),
    active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table if not exists classes (
    id bigint auto_increment primary key,
    code varchar(32) not null unique,
    name varchar(120) not null,
    category varchar(40) not null,
    weekday varchar(20),
    start_time time,
    end_time time,
    teacher_id bigint,
    status varchar(30) not null default 'PREPARING',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_classes_teacher foreign key (teacher_id) references users(id)
);

create table if not exists class_enrollments (
    id bigint auto_increment primary key,
    class_id bigint not null,
    student_id bigint not null,
    enrolled_at timestamp not null default current_timestamp,
    active boolean not null default true,
    constraint uk_class_enrollments unique (class_id, student_id),
    constraint fk_enrollments_class foreign key (class_id) references classes(id),
    constraint fk_enrollments_student foreign key (student_id) references students(id)
);

create table if not exists attendance (
    id bigint auto_increment primary key,
    class_id bigint not null,
    student_id bigint not null,
    session_date date not null,
    status varchar(20) not null,
    arrival_time time,
    note varchar(500),
    recorded_by bigint not null,
    recorded_at timestamp not null default current_timestamp,
    line_notification_status varchar(30) not null default 'PENDING',
    constraint uk_attendance unique (class_id, student_id, session_date),
    constraint fk_attendance_class foreign key (class_id) references classes(id),
    constraint fk_attendance_student foreign key (student_id) references students(id),
    constraint fk_attendance_user foreign key (recorded_by) references users(id)
);

create table if not exists leave_requests (
    id bigint auto_increment primary key,
    student_id bigint not null,
    class_id bigint,
    leave_date date not null,
    reason_type varchar(30) not null,
    reason_text varchar(500),
    status varchar(20) not null default 'PENDING',
    source varchar(30) not null default 'MANUAL',
    review_note varchar(500),
    reviewed_by bigint,
    reviewed_at timestamp,
    enrolled_student_id bigint,
    enrolled_class_id bigint,
    enrolled_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_leave_student foreign key (student_id) references students(id),
    constraint fk_leave_class foreign key (class_id) references classes(id),
    constraint fk_leave_reviewer foreign key (reviewed_by) references users(id)
);

create table if not exists announcements (
    id bigint auto_increment primary key,
    title varchar(160) not null,
    category varchar(40) not null,
    body text not null,
    image_url varchar(255),
    published boolean not null default false,
    published_at timestamp,
    created_by bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_announcements_user foreign key (created_by) references users(id)
);

create table if not exists registration_requests (
    id bigint auto_increment primary key,
    status varchar(30) not null default 'NEW',
    inquiry_type varchar(50) not null,
    current_need varchar(80),
    program_interests varchar(500) not null,
    student_chinese_name varchar(80) not null,
    student_english_name varchar(80),
    grade_level varchar(40) not null,
    school varchar(80),
    english_level varchar(800),
    parent_name varchar(80) not null,
    parent_phone varchar(40) not null,
    parent_line_id varchar(128),
    preferred_contact_time varchar(255),
    contact_preference varchar(80),
    need_homework_care varchar(30),
    need_pickup_support varchar(30),
    available_times varchar(500),
    notes varchar(1000),
    referral_source varchar(120),
    photo_consent varchar(30) not null default 'UNASKED',
    privacy_accepted boolean not null default false,
    source varchar(30) not null default 'WEB',
    review_note varchar(500),
    reviewed_by bigint,
    reviewed_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_registration_reviewer foreign key (reviewed_by) references users(id)
);

create table if not exists payroll_entries (
    id bigint auto_increment primary key,
    teacher_id bigint not null,
    work_date date not null,
    class_label varchar(120),
    start_time time,
    end_time time,
    hours decimal(6,2) not null,
    hourly_rate decimal(10,2) not null,
    amount decimal(12,2) not null,
    note varchar(500),
    status varchar(30) not null default 'DRAFT',
    created_by bigint,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_payroll_teacher foreign key (teacher_id) references users(id),
    constraint fk_payroll_creator foreign key (created_by) references users(id)
);

create table if not exists syllabus_import_batches (
    id bigint auto_increment primary key,
    original_filename varchar(255) not null,
    sheet_name varchar(120) not null,
    imported_by bigint,
    imported_at timestamp not null default current_timestamp,
    total_lessons int not null default 0,
    approved_count int not null default 0,
    draft_count int not null default 0,
    review_count int not null default 0,
    sync_roster boolean not null default false,
    roster_student_count int not null default 0,
    roster_class_count int not null default 0,
    roster_enrollment_count int not null default 0,
    warning_count int not null default 0,
    warnings_text text,
    active boolean not null default false,
    status varchar(30) not null default 'IMPORTED',
    activated_by bigint,
    activated_at timestamp,
    superseded_by bigint,
    superseded_at timestamp,
    constraint fk_syllabus_import_user foreign key (imported_by) references users(id),
    constraint fk_syllabus_import_activated_by foreign key (activated_by) references users(id),
    constraint fk_syllabus_import_superseded_by foreign key (superseded_by) references users(id)
);

create table if not exists syllabus_import_actions (
    id bigint auto_increment primary key,
    batch_id bigint not null,
    action_type varchar(40) not null,
    actor_id bigint,
    action_at timestamp not null default current_timestamp,
    note varchar(500),
    constraint fk_syllabus_import_action_batch foreign key (batch_id) references syllabus_import_batches(id),
    constraint fk_syllabus_import_action_actor foreign key (actor_id) references users(id)
);

create table if not exists lesson_plans (
    id bigint auto_increment primary key,
    import_batch_id bigint not null,
    lesson_date date not null,
    class_label varchar(120) not null,
    teacher_names varchar(255),
    content text not null,
    approval_status varchar(30) not null,
    source_sheet varchar(120) not null,
    source_row_number int not null,
    source_column_label varchar(8) not null,
    approval_source_value varchar(80),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_lesson_plans_import_batch foreign key (import_batch_id) references syllabus_import_batches(id)
);

create table if not exists learning_posts (
    id bigint auto_increment primary key,
    lesson_date date not null,
    class_label varchar(120) not null,
    category varchar(40) not null default 'GENERAL',
    title varchar(160) not null,
    vocabulary_text text,
    sentence_pattern text,
    homework_note text,
    teacher_note text,
    status varchar(30) not null default 'DRAFT',
    pinned boolean not null default false,
    published_at timestamp,
    created_by bigint,
    updated_by bigint,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_learning_created_by foreign key (created_by) references users(id),
    constraint fk_learning_updated_by foreign key (updated_by) references users(id)
);

alter table students add column if not exists school varchar(80);
alter table students add column if not exists grade_level varchar(40);
alter table students add column if not exists import_source varchar(40);
alter table students add column if not exists pickup_note varchar(500);
alter table students add column if not exists emergency_contact_name varchar(80);
alter table students add column if not exists emergency_contact_phone varchar(40);
alter table leave_requests add column if not exists source varchar(30) not null default 'MANUAL';
alter table leave_requests add column if not exists review_note varchar(500);
alter table leave_requests add column if not exists updated_at timestamp not null default current_timestamp;
alter table registration_requests add column if not exists current_need varchar(80);
alter table registration_requests add column if not exists source varchar(30) not null default 'WEB';
alter table registration_requests add column if not exists review_note varchar(500);
alter table registration_requests add column if not exists reviewed_by bigint;
alter table registration_requests add column if not exists reviewed_at timestamp;
alter table registration_requests add column if not exists photo_consent varchar(30) not null default 'UNASKED';
alter table registration_requests add column if not exists enrolled_student_id bigint;
alter table registration_requests add column if not exists enrolled_class_id bigint;
alter table registration_requests add column if not exists enrolled_at timestamp;
alter table registration_requests add column if not exists updated_at timestamp not null default current_timestamp;
alter table lesson_plans add column if not exists teacher_names varchar(255);
alter table syllabus_import_batches add column if not exists sync_roster boolean not null default false;
alter table syllabus_import_batches add column if not exists roster_student_count int not null default 0;
alter table syllabus_import_batches add column if not exists roster_class_count int not null default 0;
alter table syllabus_import_batches add column if not exists roster_enrollment_count int not null default 0;
alter table syllabus_import_batches add column if not exists active boolean not null default false;
alter table syllabus_import_batches add column if not exists activated_by bigint;
alter table syllabus_import_batches add column if not exists activated_at timestamp;
alter table syllabus_import_batches add column if not exists superseded_by bigint;
alter table syllabus_import_batches add column if not exists superseded_at timestamp;
alter table learning_posts add column if not exists teacher_note text;
alter table learning_posts add column if not exists pinned boolean not null default false;
