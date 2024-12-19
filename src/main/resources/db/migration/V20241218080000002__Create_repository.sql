CREATE TABLE IF NOT EXISTS repository(

     id uuid,
     name varchar,
     report uuid
);

CREATE TABLE IF NOT EXISTS report(

     id uuid,
     name varchar,
     template_id uuid,
     repository uuid
);

ALTER TABLE repository  ADD CONSTRAINT pk_repository  PRIMARY KEY (id);
ALTER TABLE report  ADD CONSTRAINT pk_report  PRIMARY KEY (id);

ALTER TABLE repository ADD CONSTRAINT fk_repository_report_report FOREIGN KEY (report) REFERENCES report(id);
ALTER TABLE report ADD CONSTRAINT fk_report_repository_repository FOREIGN KEY (repository) REFERENCES repository(id);