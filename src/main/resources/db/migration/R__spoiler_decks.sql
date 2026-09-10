-- Managed spoiler deck updates. Keep this file after retiring all sections.
-- Retire each section alongside the migration from temporary to final card IDs.
-- Fixed quantities preserve unrelated cards; configured decks must already exist.

-- BEGIN Spoiler:NB / preconstructed-noites_brasileiras
-- Academia de Campeões, São Paulo
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150001, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Bar Último Trago, Campina Grande
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150002, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Convento da Penha, Vila Velha
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150003, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Destilaria Estrela da Paraíba
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150004, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Iara
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150005, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Meia Lua
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150006, 4)
ON DUPLICATE KEY UPDATE number = 4;

-- Mulher de Branco
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150007, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Cãoera
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150013, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Porto de Santos
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150015, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Palavra Sagrada, Rio de Janeiro
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150018, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Chave de Braço
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150019, 3)
ON DUPLICATE KEY UPDATE number = 3;

-- Hálito Negro
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 150020, 3)
ON DUPLICATE KEY UPDATE number = 3;

-- Caique
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250001, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Luana do Lago
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250002, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Luíza
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250003, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Maria Quitéria
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250004, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Mel, a Guerreira Vibrante
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250005, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- O Encourado
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250006, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Sydnelson
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250007, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Rey, o Mentor
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250014, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Ariete
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250015, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Eduardo, o Oráculo Mascarado
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250016, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Marcela
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250017, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- O Grande Arquiteto
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250020, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Tereza Rossi
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250021, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Dorinha, a Confusa
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-noites_brasileiras', 250025, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- END Spoiler:NB / preconstructed-noites_brasileiras

-- BEGIN Spoiler:SNY / preconstructed-secrets_of_new_york
-- Information Exchange
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 150008, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Backroom Deal
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 150009, 4)
ON DUPLICATE KEY UPDATE number = 4;

-- St. Patrick's Cathedral, New York
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 150010, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Aaron Fletcher, Silent Investor
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 150011, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Double Spiral Corporation
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 150012, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- The Art Hole, New York
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 150014, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Father Anthony
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 150016, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Deadly Secrets
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 150017, 4)
ON DUPLICATE KEY UPDATE number = 4;

-- Kaiser
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250008, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Julia Sowinski
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250009, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Qadir al-Asmai
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250010, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Tharmas
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250011, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Hellene Panhard
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250012, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Hope
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250013, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Gianni D'Angelo
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250018, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Thomas Arturo
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250019, 2)
ON DUPLICATE KEY UPDATE number = 2;

-- Katherine Wiese
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250022, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Padraic Conroy
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250023, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- Kali
INSERT INTO deck_card (deck_id, id, number)
VALUES ('preconstructed-secrets_of_new_york', 250024, 1)
ON DUPLICATE KEY UPDATE number = 1;

-- END Spoiler:SNY / preconstructed-secrets_of_new_york
