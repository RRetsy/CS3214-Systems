/*
 * Widgets that plot data obtained from the sys stat web service.
 * 
 * Supports
 *
 * <div id="meminfo"> </div>
 * <div id="loadavg"> </div>
 *
 * Written by Godmar Back for CS 3214 Fall 2009
 */
(function () {

var jqplotbase = "/files/widget/";

// loadScript taken from 
// http://devblog.techhead.biz/2009/04/dynamically-load-external-javascript-in.html
// (code mirrors $.getScript)
function loadScript(src, callback) {
  var head = document.getElementsByTagName('head')[0];
  var script = document.createElement('script');
  var loaded = false;
  script.setAttribute('src', src);
  script.onload = script.onreadystatechange = function() {
    if (!loaded && (!this.readyState || this.readyState == 'complete'
                                     || this.readyState == 'loaded') ) {
      loaded = true;
      callback();
      script.onload = script.onreadystatechange = null;
      head.removeChild(script);
    }
  }
  head.appendChild(script);
}

/*
 * A Delayer object invokes a callback passed to the constructor
 * after the following two conditions are true:
 * - every function returned from a call to add() has been called
 * - the ready() method has been called.
 */
var Delayer = function (cb) {
    var count = 0;
    var finalized = false;

    this.add = function () {
        count++;

        return function () {
            count--;
            if (count == 0 && finalized)
                cb();
        }
    }

    this.ready = function () {
        finalized = true;
        if (count == 0)
            cb();
    }
}

function dowithJQuery($) {

    function updateLoadaveragePlot(plot, divid, value, nvalues, title) {
        if (plot) {
            var data = plot.series[0].data;
            for (var i = 0; i < data.length - 1; i++)
                data[i][1] = data[i+1][1];
            data[i][1] = value;

            var ymax = 1.0;
            $.each(data, function (index, el) {
                if (el[1] > ymax) ymax = el[1];
            });

            plot.axes.yaxis.max = ymax;
            plot.title.text = title;
            plot.replot();
            return plot;
        }

        var data = [value];
        for (var i = 2; i < nvalues; i++)
            data.push(0.0);

        return $.jqplot(divid, [data], {
            seriesDefaults:{neighborThreshold:0, showMarker: false},
            series:[
                {
                    fill: true, fillAlpha:0.8, 
                    shadow:false, showLabel: false,
                }
            ],
            legend: {
              location:'nw',
              xoffset: 310,
              yoffset: 100
            },
            axesDefaults:{
                autoscale: true, 
                useSeriesColor: true
            },
            title : title,
            axes: {
                yaxis: {
                    tickOptions: {formatString:"%.1f"}, 
                    min: -0.001
                },
                xaxis: {
                    min: 1, max: nvalues,
                    showTicks: false
                }
            }
        });
    }

    function showMemory(plot, divid, vallabel, axismax, title) {

        if (plot) {
            for (var i = 0; i < vallabel.length; i++)
                plot.series[i].data[0][1] = vallabel[i].value;

            plot.axes.yaxis.max = axismax;
            plot.replot();
            return plot;
        }

        var data = [];
        for (var i = 0; i < vallabel.length; i++)
            data.push([ vallabel[i].value ]);

        var labels = [];
        for (var i = 0; i < vallabel.length; i++)
            labels.push({ label: vallabel[i].label });

        return $.jqplot(divid, data, {
            seriesColors: [ "#8B3A3A", "#CD5555", "#000080", "#008B00" ],
            stackSeries: true,
            legend: {
                show: true,
                location: 'nw',
                // xoffset: 115
            },
            seriesDefaults: {
                renderer: $.jqplot.BarRenderer,
                rendererOptions: {
                    barPadding: 2,
                    barMargin: 10
                }
            },
            series: labels,
            axes: {
                xaxis: {
                    renderer: $.jqplot.CategoryAxisRenderer,
                    ticks: [title]
                },
                yaxis: {
                    numberTicks: 6,
                    tickOptions: {
                        formatString: "%.0dM"
                    },
                    min: 0, 
                    max: axismax
                }
            }
        });
    }

    function renderWidgets($) {
        $('#meminfo').each(function () {
            var $div = $(this);
            var url = $div.attr('url');
            var updateInterval = Number($div.attr('update'));
            var plot = undefined;

            function update () {
                $.getJSON(url + "/meminfo?callback=?", function (data) {
                    var MB = 1024;
                    plot = showMemory(plot, $div.attr('id'),
                        [
                            { value: data.Cached / MB, label: "Cached" }, 
                            { value: data.Buffers / MB, label: "Buffers" }, 
                            { value: (data.MemTotal 
                                      - data.MemFree 
                                      - data.Cached 
                                      - data.Buffers) / MB, label: "Anonymous" }, 
                            { value: data.MemFree / MB, label: "Free" }
                        ],
                        data.MemTotal / MB, url.match(/http:\/\/(.*):\d+/)[1]
                    );
                });
            }
            update ();
            setInterval(update, updateInterval);
        });

        $('.loadavg-text').each(function () {
            var $span = $(this);
            var url = $span.attr('url');
            var updateInterval = Number($span.attr('update'));

            function update () {
                $.getJSON(url + "/loadavg?callback=?", function (data) {
                    $span.text(
                        "Load Average: " + data.loadavg.join(" ")
                        + " Threads: " + data.running_threads
                        + "/" + data.total_threads
                    );
                });
            }
            update ();
            setInterval(update, updateInterval);
        });

        $('#loadavg').each(function () {
            var $div = $(this);
            var url = $div.attr('url');
            var updateInterval = Number($div.attr('update'));
            var plot = undefined;

            function update () {
                $.getJSON(url + "/loadavg?callback=?", function (data) {
                    plot = updateLoadaveragePlot(
                            plot, $div.attr('id'), Number(data.loadavg[0]), 
                            $div.width(),   // # values, 1 per pixel
                            url.match(/http:\/\/(.*):\d+/)[1] + ": " +
                            data.running_threads + "/" + data.total_threads);
                });
            }
            update ();
            setInterval(update, updateInterval);
        });
    };

    var delay = new Delayer(function () {
        renderWidgets($);
    });

    $('head').append('<link rel="stylesheet" type="text/css"'
                    +' href="' + jqplotbase + 'jquery.jqplot.min.css" />');

    if ($.browser.msie)
        $.getScript(jqplotbase + "excanvas.js", delay.add());

    var jqplotloaded = delay.add();
    $.getScript(jqplotbase + "jquery.jqplot.js", function () {
        $.each([ "plugins/jqplot.barRenderer.js", 
                 "plugins/jqplot.categoryAxisRenderer.js"
               ],
               function (index, jsfile) {
                    $.getScript(jqplotbase + jsfile, delay.add());
               });

        jqplotloaded();
    });

    $(document).ready(delay.add());
    delay.ready();
}

loadScript(
    jqplotbase + 'jquery.min.js',
    function() {
      // rename jQuery to jQuerySysStatWidget, remove both reference
      // to jQuery and $; the jqplot plug-ins were changed to attach to
      // jQuerySysStatWidget instead of jQuery
      jQuerySysStatWidget = jQuery.noConflict(true);
      dowithJQuery(jQuerySysStatWidget);
    }
);

}) ();
