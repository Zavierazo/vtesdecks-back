-- Managed spoiler deck updates. Keep this file after retiring all sections.
-- Retire each section alongside the migration from temporary to final card IDs.
-- Fixed quantities preserve unrelated cards; missing decks are left unchanged.

-- BEGIN Spoiler:NB / preconstructed-noites_brasileiras
-- Academia de Campeões, São Paulo
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150001, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Bar Último Trago, Campina Grande
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150002, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Convento da Penha, Vila Velha
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150003, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Destilaria Estrela da Paraíba
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150004, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Iara
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150005, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Meia Lua
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150006, 4
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 4;

-- Mulher de Branco
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150007, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Cãoera
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150013, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Porto de Santos
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150015, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Palavra Sagrada, Rio de Janeiro
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150018, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Chave de Braço
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150019, 3
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 3;

-- Hálito Negro
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150020, 3
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 3;

-- Caique
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250001, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Luana do Lago
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250002, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- Luíza
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250003, 1
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 1;

-- Maria Quitéria
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250004, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- Mel, a Guerreira Vibrante
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250005, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- O Encourado
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250006, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- Sydnelson
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250007, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- Rey, o Mentor
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250014, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- Ariete
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250015, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- Eduardo, o Oráculo Mascarado
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250016, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- Marcela
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250017, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- O Grande Arquiteto
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250020, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- Tereza Rossi
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250021, 2
FROM deck d
WHERE d.id = 'preconstructed-noites_brasileiras'
ON DUPLICATE KEY UPDATE number = 2;

-- END Spoiler:NB / preconstructed-noites_brasileiras

-- BEGIN Spoiler:SNY / preconstructed-secrets_of_new_york
-- Information Exchange
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150008, 2
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 2;

-- Backroom Deal
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150009, 4
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 4;

-- St. Patrick's Cathedral, New York
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150010, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- Aaron Fletcher, Silent Investor
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150011, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- Double Spiral Corporation
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150012, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- The Art Hole, New York
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150014, 2
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 2;

-- Father Anthony
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150016, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- Deadly Secrets
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 150017, 4
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 4;

-- Kaiser
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250008, 2
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 2;

-- Julia Sowinski
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250009, 2
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 2;

-- Qadir al-Asmai
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250010, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- Tharmas
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250011, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- Hellene Panhard
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250012, 2
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 2;

-- Hope
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250013, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- Gianni D'Angelo
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250018, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- Thomas Arturo
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250019, 2
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 2;

-- Katherine Wiese
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250022, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- Padraic Conroy
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250023, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- Kali
INSERT INTO deck_card (deck_id, id, number)
SELECT d.id, 250024, 1
FROM deck d
WHERE d.id = 'preconstructed-secrets_of_new_york'
ON DUPLICATE KEY UPDATE number = 1;

-- END Spoiler:SNY / preconstructed-secrets_of_new_york
