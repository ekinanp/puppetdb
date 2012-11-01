;; ## Fact query generation

(ns com.puppetlabs.puppetdb.query.facts
  (:refer-clojure :exclude [case compile conj! distinct disj! drop sort take])
  (:require [clojure.string :as string]
            [com.puppetlabs.jdbc :as sql])
  (:use clojureql.core
        [com.puppetlabs.puppetdb.query :only [fact-query->sql fact-operators-v2]]))

(defn facts-for-node
  "Fetch the facts for the given node, as a map of `{fact value}`. This is used
  for the deprecated v1 facts API."
  [node]
  {:pre  [(string? node)]
   :post [(map? %)]}
  (let [facts (-> (table :certname_facts)
                  (project [:fact, :value])
                  (select (where (= :certname node))))]
    (into {} (for [fact @facts]
               [(:fact fact) (:value fact)]))))

(defn flat-facts-by-node
  "Similar to `facts-for-node`, but returns facts in the form:

    [{:node <node> :fact <fact> :value <value>}
     ...
     {:node <node> :fact <fact> :value <value>}]"
  [node]
  (-> (table :certname_facts)
      (project [[:certname :as :node] :fact :value])
      (select (where (= :certname node)))
      (deref)))

(defn fact-names
  "Returns the distinct list of known fact names, ordered alphabetically
  ascending. This includes facts which are known only for deactivated nodes."
  []
  {:post [(coll? %)
          (every? string? %)]}
  (let [facts (-> (table :certname_facts)
                  (project [:fact])
                  (distinct)
                  (order-by [:fact]))]
    (map :fact @facts)))

(defn query->sql
  "Compile a query into an SQL expression."
  [query]
  {:pre [((some-fn nil? sequential?) query) ]
   :post [(vector? %)
          (string? (first %))
          (every? (complement coll?) (rest %))]}
  (if query
    (let [[subselect & params] (fact-query->sql fact-operators-v2 query)
          sql (format "SELECT certname AS node, fact, value FROM (%s) subquery1 ORDER BY node, fact, value" subselect)]
      (apply vector sql params))
    ["SELECT certname AS node, fact, value FROM certname_facts ORDER BY node, certname_facts.fact, certname_facts.value"]))

(defn query-facts
  [[sql & params]]
  {:pre [(string? sql)]}
  (apply sql/query-to-vec sql params))
