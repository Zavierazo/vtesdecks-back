-- Keep deck_user rows and timestamps because they also record deck visits.
UPDATE `deck_user` du
JOIN `deck` d ON d.`id` = du.`deck_id` AND d.`user` = du.`user`
SET du.`rate` = NULL,
    du.`favorite` = 0,
    du.`modification_date` = du.`modification_date`
WHERE du.`rate` IS NOT NULL OR du.`favorite` <> 0;

-- Comment reactions are deliberately unaffected.
DELETE r FROM `reaction` r
JOIN `deck` d ON d.`id` = r.`target_id` AND d.`user` = r.`user`
WHERE r.`target_type` = 'DECK';
