package com.vtesdecks.jpa.repositories;

import com.google.common.base.Splitter;
import com.vtesdecks.enums.WishlistPriority;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.WishlistCardEntity;
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
public class WishlistCardRepositoryImpl implements WishlistCardRepositoryCustom {

    private static final String CARD_ID = "cardId";
    private static final String NUMBER = "number";
    private static final String PRIORITY = "priority";
    private static final int CRYPT_ID_THRESHOLD = 200000;

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<WishlistCardEntity> findByDynamicFilters(Integer userId, Map<String, String> filters, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<WishlistCardEntity> cq = cb.createQuery(WishlistCardEntity.class);
        Root<WishlistCardEntity> root = cq.from(WishlistCardEntity.class);
        cq.where(getPredicates(cb, root, userId, filters).toArray(new Predicate[0]));
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            pageable.getSort().forEach(order -> {
                if (order.getProperty().equals("cardName")) {
                    Expression<Object> cardName = getCardNameJoin(cb, root);
                    orders.add(order.isAscending() ? cb.asc(cardName) : cb.desc(cardName));
                } else if (order.getProperty().equals(PRIORITY)) {
                    // Nulls last regardless of direction, then by rank (ordinal LOW=0 < MEDIUM=1 < HIGH=2)
                    Expression<Object> nullsLast = cb.selectCase()
                            .when(cb.isNull(root.get(PRIORITY)), 1)
                            .otherwise(0);
                    orders.add(cb.asc(nullsLast));
                    orders.add(order.isAscending() ? cb.asc(root.get(PRIORITY)) : cb.desc(root.get(PRIORITY)));
                } else if (order.getProperty().equals("price") || order.getProperty().equals("totalPrice")) {
                    Expression<Number> cardPrice = getCardPriceJoin(cq, cb, root, order.getProperty().equals("totalPrice"), root.get(NUMBER));
                    orders.add(order.isAscending() ? cb.asc(cardPrice) : cb.desc(cardPrice));
                } else {
                    orders.add(order.isAscending() ? cb.asc(root.get(order.getProperty())) : cb.desc(root.get(order.getProperty())));
                }
            });
            cq.orderBy(orders);
        }

        List<WishlistCardEntity> resultList = em.createQuery(cq)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<WishlistCardEntity> countRoot = countQuery.from(WishlistCardEntity.class);
        countQuery.select(cb.count(countRoot)).where(getPredicates(cb, countRoot, userId, filters).toArray(new Predicate[0]));
        Long total = em.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(resultList, pageable, total);
    }

    private static Expression<Object> getCardNameJoin(CriteriaBuilder cb, Root<WishlistCardEntity> root) {
        Join<WishlistCardEntity, Object> libraryJoin = root.join("library", JoinType.LEFT);
        Join<WishlistCardEntity, Object> cryptJoin = root.join("crypt", JoinType.LEFT);
        return cb.selectCase()
                .when(cb.lessThan(root.get(CARD_ID), CRYPT_ID_THRESHOLD), libraryJoin.get("name"))
                .otherwise(cryptJoin.get("name"));
    }

    private static Expression<Number> getCardPriceJoin(CriteriaQuery<?> cq, CriteriaBuilder cb, Root<WishlistCardEntity> root, boolean totalPrice, Expression<Number> numberExpression) {
        Subquery<Number> minPriceSubquery = cq.subquery(Number.class);
        Root<CardShopEntity> shopRoot = minPriceSubquery.from(CardShopEntity.class);
        minPriceSubquery.select(cb.min(shopRoot.get("price")));
        minPriceSubquery.where(cb.equal(shopRoot.get(CARD_ID), root.get(CARD_ID)));
        if (totalPrice) {
            return cb.prod(minPriceSubquery, numberExpression);
        }
        return minPriceSubquery;
    }

    private static List<Predicate> getPredicates(CriteriaBuilder cb, Root<WishlistCardEntity> root, Integer userId, Map<String, String> filters) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("userId"), userId));
        filters.forEach((field, value) -> {
            if (field != null && value != null) {
                if (field.equals("cardType")) {
                    if (value.equals("library")) {
                        predicates.add(cb.lessThan(root.get(CARD_ID), CRYPT_ID_THRESHOLD));
                    } else {
                        predicates.add(cb.greaterThanOrEqualTo(root.get(CARD_ID), CRYPT_ID_THRESHOLD));
                    }
                } else if (field.equals("cardName")) {
                    Join<WishlistCardEntity, Object> libraryJoin = root.join("library", JoinType.LEFT);
                    Join<WishlistCardEntity, Object> cryptJoin = root.join("crypt", JoinType.LEFT);
                    predicates.add(cb.or(
                            cb.like(libraryJoin.get("name").as(String.class), "%" + value + "%"),
                            cb.like(cryptJoin.get("name").as(String.class), "%" + value + "%")
                    ));
                } else if (field.equals(PRIORITY)) {
                    if (value.contains(",")) {
                        List<WishlistPriority> values = Splitter.on(',').trimResults().splitToList(value).stream()
                                .map(v -> WishlistPriority.valueOf(v.toUpperCase()))
                                .toList();
                        predicates.add(root.get(PRIORITY).in(values));
                    } else if (value.isEmpty() || value.equalsIgnoreCase("null") || value.equalsIgnoreCase("none")) {
                        predicates.add(cb.isNull(root.get(PRIORITY)));
                    } else {
                        predicates.add(cb.equal(root.get(PRIORITY), WishlistPriority.valueOf(value.toUpperCase())));
                    }
                } else if (value.contains(",")) {
                    List<String> values = Splitter.on(',').trimResults().splitToList(value);
                    predicates.add(root.get(field).in(values));
                } else {
                    if (value.isEmpty() || value.equalsIgnoreCase("null") || value.equalsIgnoreCase("none") || value.equals("0")) {
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
