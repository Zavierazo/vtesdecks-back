package com.vtesdecks.jpa.repositories;// CollectionCardRepositoryImpl.java

import com.google.common.base.Splitter;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.CollectionCardEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class CollectionCardRepositoryImpl implements CollectionCardRepositoryCustom {

    public static final String CARD_ID = "cardId";
    public static final String SET = "set";
    public static final String BINDER_ID = "binderId";
    public static final String NUMBER = "number";

    @PersistenceContext
    private EntityManager em;

    public Page<CollectionCardEntity> findByDynamicFiltersGroupBy(Integer collectionId, Map<String, String> filters, String groupBy, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<CollectionCardEntity> root = cq.from(CollectionCardEntity.class);
        switch (groupBy) {
            case SET:
                cq.multiselect(root.get(CARD_ID), root.get(SET), cb.sum(root.get(NUMBER)));
                break;
            case BINDER_ID:
                cq.multiselect(root.get(CARD_ID), root.get(BINDER_ID), cb.sum(root.get(NUMBER)));
                break;
            case CARD_ID:
            default:
                cq.multiselect(root.get(CARD_ID), cb.sum(root.get(NUMBER)));
        }
        cq.where(getPredicates(cb, root, collectionId, filters).toArray(new Predicate[0]));
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            pageable.getSort().forEach(order -> {
                if (order.getProperty().equals("cardName")) {
                    Expression<Object> cardName = getCardNameJoin(cb, root);
                    if (order.isAscending()) {
                        orders.add(cb.asc(cardName));
                    } else {
                        orders.add(cb.desc(cardName));
                    }
                } else if (order.getProperty().equals("number")) {
                    Expression<Number> cardNumber = cb.sum(root.get(NUMBER));
                    if (order.isAscending()) {
                        orders.add(cb.asc(cardNumber));
                    } else {
                        orders.add(cb.desc(cardNumber));
                    }
                } else if (order.getProperty().equals("price") || order.getProperty().equals("totalPrice")) {
                    Expression<Number> cardPrice = getCardPriceJoin(cq, cb, root, order.getProperty().equals("totalPrice"));
                    if (order.isAscending()) {
                        orders.add(cb.asc(cardPrice));
                    } else {
                        orders.add(cb.desc(cardPrice));
                    }
                } else {
                    if (order.isAscending()) {
                        orders.add(cb.asc(root.get(order.getProperty())));
                    } else {
                        orders.add(cb.desc(root.get(order.getProperty())));
                    }
                }
            });
            cq.orderBy(orders);
        }
        switch (groupBy) {
            case SET:
                cq.groupBy(root.get(CARD_ID), root.get(SET));
                break;
            case BINDER_ID:
                cq.groupBy(root.get(CARD_ID), root.get(BINDER_ID));
                break;
            case CARD_ID:
            default:
                cq.groupBy(root.get(CARD_ID));
        }

        List<Object[]> resultList = em.createQuery(cq)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<CollectionCardEntity> collectionCardEntities = resultList.stream()
                .map(result -> {
                    CollectionCardEntity card = new CollectionCardEntity();
                    card.setCardId((Integer) result[0]);
                    switch (groupBy) {
                        case SET:
                            card.setSet((String) result[1]);
                            card.setNumber((Integer) result[2]);
                            break;
                        case BINDER_ID:
                            card.setBinderId((Integer) result[1]);
                            card.setNumber((Integer) result[2]);
                            break;
                        case CARD_ID:
                        default:
                            card.setNumber((Integer) result[1]);
                    }
                    card.setCollectionId(collectionId);
                    return card;
                })
                .toList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<CollectionCardEntity> countRoot = countQuery.from(CollectionCardEntity.class);
        long total;
        switch (groupBy) {
            case SET:
                countQuery.select(cb.count(cb.nullLiteral(Object.class)))
                        .groupBy(countRoot.get(CARD_ID), countRoot.get(SET));
                total = em.createQuery(countQuery).getResultList().size();
                break;
            case BINDER_ID:
                countQuery.select(cb.count(cb.nullLiteral(Object.class)))
                        .groupBy(countRoot.get(CARD_ID), countRoot.get(BINDER_ID));
                total = em.createQuery(countQuery).getResultList().size();
                break;
            case CARD_ID:
            default:
                countQuery.select(cb.countDistinct(countRoot.get(CARD_ID)));
                total = em.createQuery(countQuery).getSingleResult();
        }
        return new PageImpl<>(collectionCardEntities, pageable, total);
    }

    @Override
    public Page<CollectionCardEntity> findByDynamicFilters(Integer collectionId, Map<String, String> filters, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<CollectionCardEntity> cq = cb.createQuery(CollectionCardEntity.class);
        Root<CollectionCardEntity> root = cq.from(CollectionCardEntity.class);
        cq.where(getPredicates(cb, root, collectionId, filters).toArray(new Predicate[0]));
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            pageable.getSort().forEach(order -> {
                if (order.getProperty().equals("cardName")) {
                    Expression<Object> cardName = getCardNameJoin(cb, root);
                    if (order.isAscending()) {
                        orders.add(cb.asc(cardName));
                    } else {
                        orders.add(cb.desc(cardName));
                    }
                } else if (order.getProperty().equals("price") || order.getProperty().equals("totalPrice")) {
                    Expression<Number> cardPrice = getCardPriceJoin(cq, cb, root, order.getProperty().equals("totalPrice"));
                    if (order.isAscending()) {
                        orders.add(cb.asc(cardPrice));
                    } else {
                        orders.add(cb.desc(cardPrice));
                    }
                } else {
                    if (order.isAscending()) {
                        orders.add(cb.asc(root.get(order.getProperty())));
                    } else {
                        orders.add(cb.desc(root.get(order.getProperty())));
                    }
                }
            });
            cq.orderBy(orders);
        }

        List<CollectionCardEntity> resultList = em.createQuery(cq)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<CollectionCardEntity> countRoot = countQuery.from(CollectionCardEntity.class);
        countQuery.select(cb.count(countRoot)).where(getPredicates(cb, countRoot, collectionId, filters).toArray(new Predicate[0]));
        Long total = em.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(resultList, pageable, total);
    }

    private static Expression<Object> getCardNameJoin(CriteriaBuilder cb, Root<CollectionCardEntity> root) {
        Join<CollectionCardEntity, Object> libraryJoin = root.join("library", JoinType.LEFT);
        Join<CollectionCardEntity, Object> cryptJoin = root.join("crypt", JoinType.LEFT);
        return cb.selectCase()
                .when(cb.lessThan(root.get(CARD_ID), 200000), libraryJoin.get("name"))
                .otherwise(cryptJoin.get("name"));
    }

    private static Expression<Number> getCardPriceJoin(CriteriaQuery<?> cq, CriteriaBuilder cb, Root<CollectionCardEntity> root, boolean totalPrice) {
        Subquery<Number> sub = cq.subquery(Number.class);
        Root<CardShopEntity> subRoot = sub.from(CardShopEntity.class);
        sub.select(cb.min(subRoot.get("price")));
        sub.where(cb.equal(subRoot.get("cardId"), root.get("cardId")));
        if (totalPrice) {
            return cb.prod(sub, root.get(NUMBER));
        } else {
            return sub;
        }
    }

    private static List<Predicate> getPredicates(CriteriaBuilder cb, Root<CollectionCardEntity> root, Integer collectionId, Map<String, String> filters) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("collectionId"), collectionId));
        filters.forEach((field, value) -> {
            if (field != null && value != null) {
                if (field.equals("cardType")) {
                    if (value.equals("library")) {
                        predicates.add(cb.lessThan(root.get(CARD_ID), 200000));
                    } else {
                        predicates.add(cb.greaterThanOrEqualTo(root.get(CARD_ID), 200000));
                    }
                } else if (field.equals("cardName")) {
                    Join<CollectionCardEntity, Object> libraryJoin = root.join("library", JoinType.LEFT);
                    Join<CollectionCardEntity, Object> cryptJoin = root.join("crypt", JoinType.LEFT);
                    predicates.add(cb.or(
                            cb.like(libraryJoin.get("name").as(String.class), "%" + value + "%"),
                            cb.like(cryptJoin.get("name").as(String.class), "%" + value + "%")
                    ));
                } else if (value.contains(",")) {
                    List<String> values = Splitter.on(',').trimResults().splitToList(value);
                    predicates.add(root.get(field).in(values));
                } else {
                    if (value.isEmpty() || value.equalsIgnoreCase("null") || value.equals("0")) {
                        predicates.add(cb.isNull(root.get(field)));
                    } else {
                        predicates.add(cb.equal(root.get(field), value));
                    }
                }
            }
        });
        return predicates;
    }
}