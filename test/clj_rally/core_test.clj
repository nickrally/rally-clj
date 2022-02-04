(ns clj-rally.core-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clj-rally.core :refer :all]))

(defn load-test-data
  ([] (load-config "resources/test-data.edn"))
  ([filename] (clojure.edn/read-string (slurp filename))))


(def subid (get-in (load-config) [:test-data :subid]))
(def workspace (get-in (load-config) [:test-data :workspace]))
(def project (get-in (load-config) [:test-data :project]))

(deftest subscription-info-test
  (let [result (subscription-info-test)]
    (is (= (get-in result [:sub-id]) subid))))

(deftest context-test
  (let [result (context)]
    (is (= (get-in result [:workspace]) workspace))
    (is (= (get-in result [:project]) project))))


(deftest read-story-test
  (let [wi-type    "HierarchicalRequirement"
        query      "(Name = \"DON'T DELETE\")"
        fetch      "Name"
        result     (read-workitem wi-type query fetch)
        name       (get-in (json/read-str result) ["QueryResult" "Results" 0 "_refObjectName"])]
    (is (= name "DON'T DELETE"))))


(subscription-info-test)
(context-test)
(read-story-test)



