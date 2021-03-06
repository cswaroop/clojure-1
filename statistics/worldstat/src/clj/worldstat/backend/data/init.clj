(ns worldstat.backend.data.init
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.tools.logging :as log]))


(defn- create-csv-data [csv-file & opts]
  (with-open [reader (io/reader csv-file)]
    (doall
      (apply csv/read-csv reader opts))))

(defn- convert-row [row]
  (when (seq (second row))
    (let [metadata { ; NOTE: Vega tooltip formatting does not accept lisp-case, must be snake-case (country_name).
                    :country_name (nth row 0)
                    :country_code (keyword (nth row 1))
                    :series-name (nth row 2)
                    :series-code (keyword (nth row 3))}
          ; drop metadata, take only years 2002-2017
          year-points (drop 4 row)
          tuples (map (fn [tuple]
                        {:year (first tuple)
                         :value (try
                                  (Double/parseDouble (second tuple))
                                  (catch NumberFormatException _ nil))})
                      (map vector (range 2002 2018) year-points))]
      (map (fn [tuple]
             (merge metadata tuple))
           tuples))))

(defn- get-na-country-codes [points]
  (reduce #(conj %1 (:country_code %2)) #{} (filter #(nil? (:value %)) points)))

(defn- filter-na-countries
  "Remove those countries that have even one :na (not available) as value."
  [points]
  (let [na-country-codes (get-na-country-codes points)]
    (remove #(na-country-codes (:country_code %)) points)))

(defn- convert-all [csv-data ids]
  (map (fn [item]
         (let [country-code (:country_code item)]
           (assoc item :country-id (country-code ids))))
       (mapcat convert-row (rest csv-data))))

(defn- get-data-points [data-files ids]
  (-> (flatten (for [file data-files]
                 (-> file
                     create-csv-data
                     (convert-all ids))))
      ; filter-na-countries
      ))

(defn- get-metadata [points]
  (reduce (fn [acc point]
            (let [country (dissoc point :year :value :series-code :series-name)
                  series (dissoc point :year :value :country_code :country_name :country-id)
                  year (dissoc point :value :series-code :series-name :country_code :country_name)]
              (-> acc
                  (update-in [:countries] conj country)
                  (update-in [:series] conj series)
                  (update-in [:years] conj (:year year)))))
          {:countries #{}
           :series #{}
           :years #{}}
          points))

(defn- series-codes-to-names [series]
  (reduce (fn [acc item]
            (assoc acc (:series-code item) (:series-name item)))
          {}
          series))

(defn- country-codes-to-names [countries]
  (reduce (fn [acc item]
            (assoc acc (:country_code item) (:country_name item)))
          {}
          countries))


(defn read-topojson-country-codes [file]
  (log/debug "ENTER read-topojson-country-codes")
  (map (fn [[name _ acr id]]
         {:acr (keyword acr)
          :id (Integer/parseInt id)
          :name name})
       ; Skip the header row.
       (rest (create-csv-data file :separator \;))))

(def non-country-codes
  #{;Continents, income groups etc, small countries etc.
    :SAS :TSA :ECA :TLA :PRE :TMN :LTE :OED :LIC :CAF :SSF :SST :EAP :TEC :LMY :LAC :NAC :LMC :EUU :EAR :FCS :WLD :UMC
    :SSA :HIC :ECS :MEA :LDC :INX :TSS :TEA :CEB :LNC :MIC :EMU :HPC :ARB :MNA :PST :EAS :LCN :PSS :CSS :ANT :CHI :OSS
    ; Couldn't find country id
    :XKX ; Kosovo
    })

(defn remove-non-country-values [values a-set]
  (remove (fn [item]
            (a-set (:country_code item)))
          values))

(defn get-data [data-files topojson-file]
  (log/debug "ENTER get-data")
  (let [topojson-country-codes (read-topojson-country-codes topojson-file)
        ids (into {} (map (juxt :acr :id) topojson-country-codes))
        data-points (get-data-points data-files ids)
        {:keys [countries series years]} (get-metadata data-points)]
    {:raw-points data-points
     :points (remove-non-country-values data-points non-country-codes)
     :countries countries
     :series series
     :years years
     :country-codes (country-codes-to-names countries)
     :series-codes (series-codes-to-names series)
     :non-country-codes non-country-codes
     :country-ids ids
     :topojson-country-codes topojson-country-codes
     }))



(comment
  (create-csv-data "data/csv/topojson-country-codes.csv")
  (read-topojson-country-codes "data/csv/topojson-country-codes.csv")
  (sort (:years (get-metadata (take 100 (:points (user/data))))))
  (:series (user/data))
  (:years (user/data))
  (:countries (user/data))
  (sort-by :country_name (:countries (user/data)))
  (:country-codes (user/data))
  (:series-codes (user/data))
  )
