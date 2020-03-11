(ns fast5watch.test.util.core
  (:require
    [clojure.test :refer :all]
    [fast5watch.util.core :refer :all]))

(deftest test-util
  (testing "Nanopore run absolute path parsing"
    (is
      (= (run-path->map "/var/lib/MinKNOW/data/2019-07-22-RUN_DESCRIPTION/SAMPLE_ID/20190722_1200_MN12345_FAK12345_deadbeef/fast5")
          {:base-path "/var/lib/MinKNOW/data",
           :name "2019-07-22-RUN_DESCRIPTION",
           :sample-id "SAMPLE_ID",
           :timestamp "20190722_1200",
           :instrument "MN12345",
           :flowcell-id "FAK12345",
           :protocol-run-id-uuid8 "deadbeef"})))
  (testing "Extract values from a collection of maps given list of keys"
    (is
      (= (vals-by-keys [:a :c :e] [{:a 1 :b 2 :c 3 :d 4 :e 5}
                                   {:a 4 :e 10}])
         '((1 3 5)
           (4 nil 10))))))