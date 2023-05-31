(ns clj-ts.exporting.page-exporter)

(defprotocol IPageExporter
  (as-map [ex])
  (page-name->export-file-path [ex page-name])
  (export-path [ex])
  (page-name->exported-link [ex page-name])
  (media-name->exported-link [ex media-name])
  (load-template [ex])
  (load-main-css [ex])
  (api-path [ex])
  (export-media-dir [ex])
  (report [ex]))