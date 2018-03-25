(ns bridge.chapter.data-test
  (:require [bridge.data.dev-data :as dev-data]
            [bridge.data.slug :as slug]
            [bridge.chapter.data :as chapter.data]
            [bridge.person.data :as person.data]
            [bridge.test.util :refer [conn db test-setup with-database]]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is join-fixtures use-fixtures]]
            [datomic.api :as d])
  (:import clojure.lang.ExceptionInfo))

(def db-name (str *ns*))

(defn person-fixtures [db-name]
  (fn [test-fn]
    (let [conn (conn db-name)]
      @(d/transact conn person.data/schema)

      (dev-data/add-person! conn
                            #:person{:name     "Test Name"
                                     :email    "test@cb.org"
                                     :password "secret"}))

    (test-fn)))

(use-fixtures :once test-setup)
(use-fixtures :each (join-fixtures [(with-database db-name chapter.data/schema)
                                    (person-fixtures db-name)]))

(def TEST-CHAPTER-TITLE "ClojureBridge Hermanus")
(def TEST-CHAPTER-SLUG (slug/->slug TEST-CHAPTER-TITLE))
(def TEST-PERSON-ID [:person/email "test@cb.org"])

(defn TEST-NEW-CHAPTER-TX
  "A function, so that spec instrumentation has a chance to work"
  []
  (chapter.data/new-chapter-tx TEST-PERSON-ID
                               #:chapter{:title TEST-CHAPTER-TITLE
                                         :location "Hermanus"}))

(deftest new-chapter-tx
  (is (thrown-with-msg? ExceptionInfo #"did not conform to spec"
                        (chapter.data/new-chapter-tx {})))

  (is (s/valid? :bridge/new-chapter-tx (TEST-NEW-CHAPTER-TX)))
  (is (= :status/active (:chapter/status (TEST-NEW-CHAPTER-TX)))))

(deftest save-chapter!

  (chapter.data/save-new-chapter! (conn db-name) (TEST-NEW-CHAPTER-TX))

  (let [new-db (db db-name)]
    (is (chapter.data/chapter-id-by-slug new-db TEST-CHAPTER-SLUG))))

(deftest person-is-organiser?

  (chapter.data/save-new-chapter! (conn db-name) (TEST-NEW-CHAPTER-TX))

  (let [new-db (db db-name)]
    (is (true? (chapter.data/person-is-organiser?
                new-db
                (chapter.data/chapter-id-by-slug new-db TEST-CHAPTER-SLUG)
                TEST-PERSON-ID)))

    (is (false? (chapter.data/person-is-organiser?
                 new-db
                 (chapter.data/chapter-id-by-slug new-db TEST-CHAPTER-SLUG)
                 123)))))

(deftest check-chapter-organiser

  (chapter.data/save-new-chapter! (conn db-name) (TEST-NEW-CHAPTER-TX))

  (let [new-db (db db-name)]
    (is (nil? (chapter.data/check-chapter-organiser
               new-db
               (chapter.data/chapter-id-by-slug new-db TEST-CHAPTER-SLUG)
               TEST-PERSON-ID)))

    (is (= {:error :bridge/not-chapter-organiser}
           (chapter.data/check-chapter-organiser
            new-db
            (chapter.data/chapter-id-by-slug new-db TEST-CHAPTER-SLUG)
            123)))))