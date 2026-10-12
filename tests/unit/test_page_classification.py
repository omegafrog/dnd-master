from preprocessing_agent.layout.page_classification import is_form_like


def test_sparse_field_geometry_is_classified_as_form():
    assert is_form_like(drawing_count=180, aligned_line_count=466,
                        long_horizontal_line_count=154, text_line_count=15)


def test_dense_diagrams_with_more_text_are_not_classified_as_forms():
    assert is_form_like(drawing_count=333, aligned_line_count=1869,
                        long_horizontal_line_count=162, text_line_count=95)
    assert is_form_like(drawing_count=416, aligned_line_count=1225,
                        long_horizontal_line_count=209, text_line_count=126)
    assert not is_form_like(drawing_count=39, aligned_line_count=953,
                            long_horizontal_line_count=10, text_line_count=42)


def test_prose_and_table_page_geometry_is_not_a_form_signal():
    for stats in (
        (8, 36, 0, 153), (2, 135, 0, 225), (5, 35, 0, 126),
        (3, 19, 0, 161), (4, 21, 0, 144),
    ):
        assert not is_form_like(drawing_count=stats[0], aligned_line_count=stats[1],
                                long_horizontal_line_count=stats[2], text_line_count=stats[3])
