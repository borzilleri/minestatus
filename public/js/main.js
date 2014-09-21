$(function() {
	var blockClass = '.server-info';
	var queryProgress = $('#server-query').html();
	var startServer = $('#server-start').html();
	var notStartable = $('#server-nostart').html();
	var infoBlock = _.template($('#info-block').html());
	var playerList = _.template($('#player-list').html());

	$(blockClass + " .panel-body").append(queryProgress);

	function updateServerBlock(block) {
		var sId = $(block).data("server-id");
		var startable = $(block).data("can-start");
		$.getJSON("/api/status/" + sId, function(data) {
			$(block).removeClass("panel-info")
				.toggleClass("panel-success", data['running'])
				.toggleClass("panel-danger", !data['running']);

			if( !data['running'] ) {
				$(block).find(".panel-body").html(startable ? startServer : notStartable);
			}
			if( data['info'] ) {
				$(block).find(".panel-title").text(data['info']['name']);
				$(block).find(".panel-body").html(infoBlock(data));
				$(block).append(playerList(data['info']));
			}
		});
	}

	$(blockClass).each(function(i, s) {
		updateServerBlock(s);
	});

	$('button.start-server').on('click', function(e) {
		e.preventDefault();

		var $s = $(e.target).closest("server-info");
		var sId = $s.data("server-id");
		$s.find('.panel-body').html(queryProgress);
		$.post("/api/start/" + sId);
		setTimeout(function() {
			updateServerBlock($s);
		}, 120000);
	});

});
