-- Small sakila-like schema used by the integration tests.
CREATE TABLE actor (
  actor_id   SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  first_name VARCHAR(45) NOT NULL,
  last_name  VARCHAR(45) NOT NULL,
  PRIMARY KEY (actor_id),
  KEY idx_actor_last_name (last_name)
) ENGINE=InnoDB;

CREATE TABLE film (
  film_id     SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title       VARCHAR(128) NOT NULL,
  description TEXT,
  rating      VARCHAR(10),
  length      SMALLINT UNSIGNED,
  PRIMARY KEY (film_id),
  KEY idx_title (title)
) ENGINE=InnoDB COMMENT='films';

CREATE TABLE film_actor (
  actor_id SMALLINT UNSIGNED NOT NULL,
  film_id  SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (actor_id, film_id),
  KEY idx_fk_film_id (film_id)
) ENGINE=InnoDB;

INSERT INTO actor (first_name, last_name) VALUES
  ('PENELOPE','GUINESS'),('NICK','WAHLBERG'),('ED','CHASE'),('JENNIFER','DAVIS'),('JOHNNY','LOLLOBRIGIDA');

INSERT INTO film (title, description, rating, length) VALUES
  ('ACADEMY DINOSAUR','A epic drama','PG',86),('ACE GOLDFINGER','A astounding epistle','G',48),
  ('ADAPTATION HOLES','A astounding reflection','NC-17',50),('AFFAIR PREJUDICE','A fanciful documentary','G',117),
  ('AFRICAN EGG','A fast-paced documentary','G',130),('AGENT TRUMAN','A intrepid panorama','PG',169);

INSERT INTO film_actor VALUES (1,1),(1,2),(2,3),(3,4),(4,5),(5,6),(2,1);

ANALYZE TABLE actor, film, film_actor;
