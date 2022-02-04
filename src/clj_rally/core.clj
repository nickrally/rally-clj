(ns clj-rally.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.edn]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn load-config
  ([] (load-config "resources/config.edn"))
  ([filename] (clojure.edn/read-string (slurp filename))))

;(get-in (load-config "resources/config.edn") [:rally :baseurl])
;(get-in (load-config) [:rally :baseurl])

(def rally (get-in (load-config) [:rally]))
;(print (get-in rally [:baseurl]))

((defn debug? []
   (if (= (get-in rally [:log-level]) "debug") true false)))

(defn- make-request
  "Function for making CRUD http requests"
  [method headers endpoint & {:keys [payload query]}]
  (log/info (format "making request: %s" endpoint))
  (:body (client/request {:headers headers
                          :method method
                          :url (str (get-in rally [:baseurl]) endpoint query)
                          :content-type "application/json"
                          :body payload
                          :debug (debug?)
                          :debug-body (debug?)})))

(defn subscription-info
  "Makes a GET request to return a hashmap of sub-id, sub-uuid and workspaces-url"
  []
  (let [sub-endpoint "subscription?fetch=subscriptionID,workspaces"
        result (make-request :get (get-in rally [:auth]) sub-endpoint)
        sub-info {:sub-id         (get-in (json/read-str result) ["Subscription" "SubscriptionID"])
                  :sub-uuid       (get-in (json/read-str result) ["Subscription" "_refObjectUUID"])
                  :workspaces-url (second (str/split (get-in (json/read-str result) ["Subscription" "Workspaces" "_ref"]) #"v2.0"))}]
    sub-info))

(defn context
  "Makes a GET request to return a hashmap of OIDs for workspace and project specified in config."
  []
  (let [headers (get-in rally [:auth])
        wrk-resource (get-in (subscription-info) [:workspaces-url])
        wrk-query    (format "&query=(Name = \"%s\")" (get-in rally [:workspace]))
        wrk-fetch    (format "?fetch=ObjectID,Projects")
        wrk-endpoint (str wrk-resource wrk-fetch)
        wrk-result   (make-request :get headers wrk-endpoint :query wrk-query)
        wrk-oid      (get-in (json/read-str wrk-result) ["QueryResult" "Results" 0 "ObjectID"])
        projects-url (get-in (json/read-str wrk-result) ["QueryResult" "Results" 0 "Projects" "_ref"])
        prj-resource (second (str/split projects-url #"v2.0"))
        prj-query    (format "&query=(Name = \"%s\")" (get-in rally [:project]))
        prj-fetch    (format "?fetch=ObjectID")
        prj-endpoint (str prj-resource prj-fetch)
        prj-result   (make-request :get headers prj-endpoint :query prj-query)
        prj-oid      (get-in (json/read-str prj-result) ["QueryResult" "Results" 0 "ObjectID"])]
    {:workspace wrk-oid :project prj-oid}))


(defn create-workitem
  "Takes two arguments: work item type and hashmap of the payload, e.g. {:Name name :PlanEstimate est}"
  [wi-type data]
  (let [record {(keyword wi-type) data}
        payload     (json/write-str record)
        context-oids (context)
        resource    "/%s/create?workspace=/workspace/%s&project=/project/%s"
        endpoint   (format resource wi-type (get context-oids :workspace) (get context-oids :project))
        result     (make-request :post (get-in rally [:auth]) endpoint :payload payload)]
    result))

(defn read-workitem
  "Takes three arguments: work item type, query string, e.g. (Name = \"DON'T DELETE\") and fetch string, e.g ObjectID,ScheduleState."
  [wi-type query-str fetch-str]
  (let [query (str "&query=" query-str)
        fetch (str "&fetch=" fetch-str)
        context-oids (context)
        resource "/%s?workspace=/workspace/%s&project=/project/%s"
        endpoint (format (str resource fetch) wi-type (get context-oids :workspace) (get context-oids :project))
        result (make-request :get (get-in rally [:auth]) endpoint :query query)]
    result))

(defn update-workitem
  "Takes three arguments: work item type, hashmap of the payload, e.g {:ScheduleState target-state} and ObjectID of workitem"
  [wi-type data oid]
  (let [record {(keyword wi-type) data}
        payload     (json/write-str record)
        endpoint   (format "/%s/%s" wi-type oid)
        result     (make-request :post (get-in rally [:auth]) endpoint :payload payload)]
    result))

(defn delete-workitem
  "Takes two arguments: work item type and ObjectID of workitem"
  [wi-type oid]
  (let [endpoint   (format "/%s/%s" wi-type oid)
        result     (make-request :delete (get-in rally [:auth]) endpoint)]
    result))

(subscription-info)
(def newItem (create-workitem "HierarchicalRequirement" {:Name "Likely3" :PlanEstimate 11}))
(def newItemID (get-in (json/read-str newItem) ["CreateResult" "Object" "ObjectID"]))
(print newItemID)
(read-workitem "HierarchicalRequirement" (str "(ObjectID = " newItemID ")") "Name,PlanEstimate,ObjectID")
(update-workitem "HierarchicalRequirement" {:ScheduleState "In-Progress"} newItemID)
(delete-workitem "HierarchicalRequirement" newItemID)

