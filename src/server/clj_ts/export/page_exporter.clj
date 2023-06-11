(ns clj-ts.export.page-exporter)

(defprotocol IPageExporter
  (as-map [page-exporter])
  (page-name->export-file-path [page-exporter page-name working-directory])
  (export-path [page-exporter])
  (page-name->exported-link [page-exporter page-name])
  (media-name->exported-link [page-exporter media-name])
  (load-template [page-exporter])
  (load-main-css [page-exporter])
  (api-path [page-exporter])
  (export-media-dir [page-exporter working-directory])
  (report [page-exporter]))