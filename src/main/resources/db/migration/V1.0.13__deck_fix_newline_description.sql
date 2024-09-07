UPDATE deck
SET description = REPLACE(description, CHAR(10), '<br/>')
